import { ref, type Ref } from 'vue'
import { AGENT_MAP, type Agent, type Message } from '@/types/api'

function generateUUID(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  agent?: Agent
  risk?: string
  thinking?: string
  isStreaming?: boolean
  seq?: number
}

export interface ToolCallInfo {
  id: string
  name: string
  args: Record<string, unknown>
  result?: string
  status: 'calling' | 'done' | 'error'
}

export interface TurnStep {
  id: string
  type: 'thinking' | 'tool_call'
  thinking?: string
  toolCall?: ToolCallInfo
  timestamp: number
}

interface UseChatStreamOptions {
  teacherId: Ref<string>
  messages: Ref<ChatMessage[]>
}

export function useChatStream({ teacherId, messages }: UseChatStreamOptions) {
  const isStreaming = ref(false)
  const activeAgent = ref<Agent | null>(null)
  const abortController = ref<AbortController | null>(null)
  const currentThreadId = ref<string | null>(null)
  const toolCalls = ref<ToolCallInfo[]>([])
  const thinkingText = ref('')
  const agentThinkingText = ref('')
  const turnSteps = ref<TurnStep[]>([])

  function buildHeaders(): Record<string, string> {
    return {
      'Content-Type': 'application/json',
      'X-Teacher-Id': teacherId.value,
    }
  }

  async function createThread(title?: string): Promise<{ id: string }> {
    const { default: axios } = await import('axios')
    const { data } = await axios.post('/api/threads', { title }, { headers: buildHeaders() })
    return data
  }

  async function deleteThread(threadId: string) {
    const { default: axios } = await import('axios')
    await axios.delete(`/api/threads/${threadId}`, { headers: buildHeaders() })
  }

  async function loadThreads() {
    const { default: axios } = await import('axios')
    const { data } = await axios.get('/api/threads', { headers: buildHeaders() })
    return data
  }

  async function loadMessages(threadId: string, beforeSeq = 0, limit = 50): Promise<Message[]> {
    const { default: axios } = await import('axios')
    const params = new URLSearchParams()
    if (beforeSeq > 0) params.set('beforeSeq', String(beforeSeq))
    params.set('limit', String(limit))
    const { data } = await axios.get(`/api/threads/${threadId}/messages?${params}`, {
      headers: buildHeaders(),
    })
    return data
  }

  async function send(
    threadId: string,
    content: string,
    deepThinking: boolean,
    onAgentChange?: (agent: Agent | null) => void,
  ) {
    if (!content.trim() || isStreaming.value) {
      console.warn('[send] blocked:', { content: content.trim(), isStreaming: isStreaming.value })
      return
    }

    console.log('[send] start:', { threadId, content })

    const userMsg: ChatMessage = {
      id: generateUUID(),
      role: 'user',
      content: content.trim(),
    }
    messages.value.push(userMsg)

    const assistantMsg: ChatMessage = {
      id: generateUUID(),
      role: 'assistant',
      content: '',
      isStreaming: true,
    }
    messages.value.push(assistantMsg)

    const assistant = messages.value[messages.value.length - 1]!

    isStreaming.value = true
    activeAgent.value = null
    abortController.value = new AbortController()
    currentThreadId.value = threadId
    toolCalls.value = []
    thinkingText.value = ''
    agentThinkingText.value = ''
    turnSteps.value = []

    let rawText = ''

    try {
      console.log('[send] fetching:', `/api/threads/${threadId}/runs/stream`)
      const response = await fetch(`/api/threads/${threadId}/runs/stream`, {
        method: 'POST',
        headers: buildHeaders(),
        body: JSON.stringify({ content: content.trim(), deepThinking }),
        signal: abortController.value.signal,
      })

      console.log('[send] response:', response.status)

      if (!response.ok) {
        const errText = await response.text().catch(() => '')
        throw new Error(`HTTP ${response.status}: ${errText}`)
      }

      if (!response.body) {
        throw new Error('响应无 body，SSE 流不可用')
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let eventType = 'message'

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          if (buffer.trim() && buffer.trim().startsWith('data:')) {
            try {
              const payload = JSON.parse(buffer.trim().slice(5).trim())
              if (eventType === 'messages-tuple' && Array.isArray(payload)) {
                for (const item of payload) {
                  if (item.type === 'ai' && item.content) {
                    rawText += item.content
                    assistant.content = rawText
                  }
                }
              }
            } catch { /* skip */ }
          }
          break
        }
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop()!

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed) {
            eventType = 'message'
            continue
          }
          if (trimmed.startsWith('event:')) {
            eventType = trimmed.slice(6).trim()
          } else if (trimmed.startsWith('data:')) {
            const dataStr = trimmed.slice(5).trim()
            try {
              const payload = JSON.parse(dataStr)

              // ── Tool call events ──
              if (eventType === 'tool_call') {
                const tc: ToolCallInfo = {
                  id: payload.id,
                  name: payload.name,
                  args: payload.args || {},
                  status: 'calling' as const,
                }
                toolCalls.value = [...toolCalls.value, tc]
                turnSteps.value = [...turnSteps.value, {
                  id: `step-${tc.id}`,
                  type: 'tool_call' as const,
                  toolCall: tc,
                  timestamp: Date.now(),
                }]
              } else if (eventType === 'tool_result') {
                const idx = toolCalls.value.findIndex(tc => tc.id === payload.id)
                if (idx >= 0) {
                  const updated = { ...toolCalls.value[idx]!, result: payload.result, status: 'done' as const }
                  toolCalls.value = toolCalls.value.map((tc, i) => i === idx ? updated : tc)
                  // Update the corresponding turn step
                  turnSteps.value = turnSteps.value.map(ts =>
                    ts.type === 'tool_call' && ts.toolCall?.id === payload.id
                      ? { ...ts, toolCall: updated }
                      : ts
                  )
                }
              }
              // ── Thinking (classification) events ──
              if (eventType === 'thinking' && payload.content) {
                thinkingText.value += payload.content
              }
              // ── Agent thinking (reasoning / tool-loop progress) ──
              if (eventType === 'agent_thinking' && payload.content) {
                agentThinkingText.value += payload.content
                // Build turnSteps: append to last thinking step or create new one
                const last = turnSteps.value[turnSteps.value.length - 1]
                if (last && last.type === 'thinking') {
                  turnSteps.value = turnSteps.value.map((ts, i) =>
                    i === turnSteps.value.length - 1
                      ? { ...ts, thinking: (ts.thinking || '') + payload.content }
                      : ts
                  )
                } else {
                  turnSteps.value = [...turnSteps.value, {
                    id: `thinking-${Date.now()}`,
                    type: 'thinking' as const,
                    thinking: payload.content,
                    timestamp: Date.now(),
                  }]
                }
              }
              // ── Stage / streaming events ──
              else if (eventType === 'stage') {
                if (payload.stage === 'STREAMING') {
                  const agent = AGENT_MAP[payload.agent]
                  if (agent) {
                    activeAgent.value = agent
                    assistant.agent = agent
                    if (payload.risk) assistant.risk = payload.risk
                    onAgentChange?.(agent)
                  }
                } else if (payload.stage === 'ERROR') {
                  assistant.content = rawText || `请求失败：${payload.message || '未知错误'}`
                }
              } else if (eventType === 'error') {
                assistant.content = rawText || `请求失败：${payload.message || '未知错误'}`
              } else if (eventType === 'messages-tuple' && Array.isArray(payload)) {
                for (const item of payload) {
                  if (item.type === 'ai' && item.content) {
                    rawText += item.content
                    assistant.content = rawText
                  }
                }
              } else if (eventType === 'content' && payload.content) {
                rawText += payload.content
                assistant.content = rawText
              } else if (eventType === 'end') {
                // done
              } else if (eventType === 'message' || !eventType) {
                if (Array.isArray(payload)) {
                  for (const item of payload) {
                    if (item.type === 'ai' && item.content) {
                      rawText += item.content
                      assistant.content = rawText
                    }
                  }
                } else if (payload.content) {
                  rawText += payload.content
                  assistant.content = rawText
                }
              }
            } catch {
              // skip non-JSON data lines
            }
            eventType = 'message'
          }
        }
      }
    } catch (err: unknown) {
      const error = err as Error & { name?: string }
      console.error('[send] error:', error.name, error.message)
      if (error.name !== 'AbortError') {
        assistant.content = rawText || `请求失败：${error.message}`
      }
    } finally {
      assistant.isStreaming = false
      isStreaming.value = false
      abortController.value = null
      activeAgent.value = null
    }
  }

  function stop() {
    abortController.value?.abort()
  }

  return {
    isStreaming,
    activeAgent,
    currentThreadId,
    toolCalls,
    thinkingText,
    agentThinkingText,
    turnSteps,
    createThread,
    deleteThread,
    loadThreads,
    loadMessages,
    send,
    stop,
  }
}
