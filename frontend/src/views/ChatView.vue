<script setup lang="ts">
import { ref, nextTick, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AGENT_MAP, type Thread, type Message } from '@/types/api'
import { useChatStream, type ChatMessage } from '@/composables/useChatStream'
import ChatSidebar from '@/components/ChatSidebar.vue'
import ChatHeader from '@/components/ChatHeader.vue'
import MessageList from '@/components/MessageList.vue'
import ChatInput from '@/components/ChatInput.vue'

import DetailPanel from '@/components/DetailPanel.vue'
import AgentGrid from '@/components/AgentGrid.vue'

const route = useRoute()
const router = useRouter()

const teacherId = ref(localStorage.getItem('teacherId') || 'anonymous')
const messages = ref<ChatMessage[]>([])
const threads = ref<Thread[]>([])
const threadTitle = ref('')
const agentPrompt = ref('')


/* ── Search results panel ── */
const searchResultsVisible = ref(false)
const searchResults = ref<Array<{ title: string; url: string; content: string }>>([])

function handleShowSearchResults(results: Array<{ title: string; url: string; content: string }>) {
  searchResults.value = results
  searchResultsVisible.value = true
}

function handleCloseSearchResults() {
  searchResultsVisible.value = false
}

const chatRef = ref<HTMLElement>()
const hasMore = ref(false)
const loadingMore = ref(false)

const { isStreaming, activeAgent, currentThreadId, toolCalls, thinkingText, agentThinkingText, turnSteps, createThread, deleteThread, loadThreads, loadMessages, send, stop } =
  useChatStream({ teacherId, messages })

function scrollDown() {
  nextTick(() => {
    if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
  })
}

/* ── Thread management ── */

let lastThreadsFetch = 0

async function refreshThreads(force = false) {
  if (!force && Date.now() - lastThreadsFetch < 30_000) return
  try {
    threads.value = await loadThreads()
    lastThreadsFetch = Date.now()
  } catch { /* network */ }
}

async function selectThread(threadId: string) {
  router.push(`/chat/${threadId}`)
}

function newThread() {
  currentThreadId.value = null
  messages.value = []
  router.push('/')
}

async function openThread(threadId: string) {
  currentThreadId.value = threadId
  hasMore.value = false
  loadingMore.value = false
  try {
    const apiMessages: Message[] = await loadMessages(threadId, 0, 50)
    hasMore.value = apiMessages.length >= 50
    messages.value = apiMessages.map(m => ({
      id: m.id,
      role: m.role,
      content: m.content,
      agent: m.agentId ? AGENT_MAP[m.agentId] : undefined,
      risk: m.risk,
      thinking: m.thinking,
      seq: m.seq,
    }))
    const t = threads.value.find(t => t.id === threadId)
    threadTitle.value = t?.title || ''
  } catch {
    messages.value = []
  }
  scrollDown()
}

/* ── Send ── */

let justCreatedThreadId: string | null = null

async function handleSend(content: string) {
  agentPrompt.value = ''
  if (!currentThreadId.value) {
    try {
      const t = await createThread()
      currentThreadId.value = t.id
      justCreatedThreadId = t.id
      router.replace(`/chat/${t.id}`)
    } catch {
      try {
        const t = await createThread()
        currentThreadId.value = t.id
        justCreatedThreadId = t.id
        router.replace(`/chat/${t.id}`)
      } catch {
        messages.value.push({
          id: 'error-' + Date.now(),
          role: 'assistant',
          content: '创建会话失败，请检查网络后刷新页面重试'
        })
        return
      }
    }
  }

  await send(currentThreadId.value, content, false, (_agent) => {
    scrollDown()
  })

  await refreshThreads()
  const t = threads.value.find(t => t.id === currentThreadId.value)
  if (t) threadTitle.value = t.title || ''
  scrollDown()
}

/* ── Agent grid select ── */

function handleAgentSelect(text: string) {
  agentPrompt.value = text
}

async function handleDeleteThread(threadId: string) {
  try {
    await deleteThread(threadId)
    if (currentThreadId.value === threadId) {
      currentThreadId.value = null
      messages.value = []
      router.replace('/')
    }
    await refreshThreads(true)
  } catch { /* ignore */ }
}

/* ── Scroll-to-top pagination ── */

async function loadMoreMessages() {
  if (!currentThreadId.value || !hasMore.value || loadingMore.value) return
  const earliest = messages.value[0]
  if (!earliest) return
  loadingMore.value = true
  try {
    const msg = await loadMessages(currentThreadId.value, earliest.seq ?? 999999, 50)
    hasMore.value = msg.length >= 50
    const prevScrollHeight = chatRef.value?.scrollHeight ?? 0
    const mapped = msg.map((m: Message) => ({
      id: m.id, role: m.role, content: m.content,
      agent: m.agentId ? AGENT_MAP[m.agentId] : undefined,
      risk: m.risk, thinking: m.thinking, seq: m.seq,
    }))
    messages.value = [...mapped, ...messages.value]
    nextTick(() => {
      if (chatRef.value) {
        chatRef.value.scrollTop = chatRef.value.scrollHeight - prevScrollHeight
      }
    })
  } catch { /* network */ }
  finally { loadingMore.value = false }
}

function handleChatScroll() {
  if (!chatRef.value) return
  if (chatRef.value.scrollTop <= 50) {
    loadMoreMessages()
  }
}

/* ── Init ── */

onMounted(async () => {
  await refreshThreads()
  const threadId = route.params.threadId as string | undefined
  if (threadId) {
    await openThread(threadId)
  }
})

/* ── Watch for route changes ── */
watch(() => route.params.threadId, async (newId) => {
  if (newId && typeof newId === 'string' && newId !== currentThreadId.value) {
    if (newId === justCreatedThreadId) {
      justCreatedThreadId = null
      return
    }
    await openThread(newId)
  }
})

/* ── Auto-scroll during streaming ── */
watch(
  () => messages.value[messages.value.length - 1]?.content,
  () => { if (isStreaming.value) scrollDown() }
)
</script>

<template>
  <div class="app-layout">
    <ChatSidebar
      :threads="threads"
      :active-thread-id="currentThreadId"
      @select-thread="selectThread"
      @new-thread="newThread"
      @delete-thread="handleDeleteThread"
    />

    <main class="main-area">
      <ChatHeader
        :title="threadTitle || (currentThreadId ? '新对话' : '')"
        :agent-name="activeAgent?.name"
      />

      <div class="main-content">
        <div class="chat-column">
          <div class="chat-area" ref="chatRef" @scroll="handleChatScroll">
            <div v-if="!currentThreadId && messages.length === 0" class="welcome">
              <div class="welcome-top">
                <h2 class="welcome-heading">你好，辅导员</h2>
                <p class="welcome-sub">选择 AI 助理或直接输入问题，我会把任务指派给最合适的 Agent 处理</p>
              </div>
              <AgentGrid @select="handleAgentSelect" />
              <ChatInput
                :is-streaming="isStreaming"
                :prefill="agentPrompt"
                @send="handleSend"
                @stop="stop"
                @update:prefill="agentPrompt = $event"
              />
              <p class="ai-disclaimer">内容由AI生成，请仔细甄别</p>
            </div>

            <template v-else>
              <MessageList
                :messages="messages"
                :teacher-label="teacherId"
                :tool-calls="toolCalls"
                :thinking-text="thinkingText"
                :agent-thinking-text="agentThinkingText"
                :turn-steps="turnSteps"
                :is-streaming="isStreaming"
                @show-search-results="handleShowSearchResults"
              />
            </template>
          </div>

          <template v-if="currentThreadId || messages.length > 0">
            <ChatInput
              :is-streaming="isStreaming"
              @send="handleSend"
              @stop="stop"
            />
            <p class="ai-disclaimer">内容由AI生成，请仔细甄别</p>
          </template>
        </div>

        <DetailPanel
          :visible="searchResultsVisible"
          :results="searchResults"
          @close="handleCloseSearchResults"
        />
      </div>
    </main>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  background: var(--bg-app);
  font-family: "PingFang SC", -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
}

.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.main-content {
  flex: 1;
  display: flex;
  min-height: 0;
}

.chat-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
}

.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  gap: 32px;
  padding: 0 48px;
}

.welcome-top {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.welcome-heading {
  font-size: 28px;
  font-weight: 800;
  color: var(--text-primary);
  margin: 0;
  letter-spacing: -0.3px;
}

.welcome-sub {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.6;
}

.welcome :deep(.composer) {
  width: 100%;
}

.ai-disclaimer {
  text-align: center;
  font-size: 11px;
  color: var(--text-tertiary);
  margin: 0;
  padding: 4px 0 8px;
}
</style>
