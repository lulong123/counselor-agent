<script setup lang="ts">
import { ref, watch, computed, reactive } from 'vue'
import type { ChatMessage, ToolCallInfo, TurnStep } from '@/composables/useChatStream'
import MessageItem from './MessageItem.vue'
import ToolCallBubble from './ToolCallBubble.vue'

const props = defineProps<{
  thinkingText: string
  agentThinkingText: string
  toolCalls: ToolCallInfo[]
  turnSteps?: TurnStep[]
  assistantMessage: ChatMessage
  isStreaming: boolean
  teacherLabel?: string
}>()

const emit = defineEmits<{
  'select-detail': [item: { title: string; url: string; content: string }]
  'show-search-results': [results: Array<{ title: string; url: string; content: string }>]
}>()

/* ── Unified thinking collapse (一级折叠) ── */
const thinkOpen = ref(true)

watch(() => props.isStreaming, (val) => {
  if (!val) {
    setTimeout(() => { thinkOpen.value = false }, 2000)
  }
})

/* ── Per-tool expand (二级折叠，默认收起) ── */
const expandedTools = reactive<Record<string, boolean>>({})

function toggleTool(id: string) {
  expandedTools[id] = !expandedTools[id]
}

const hasTurnSteps = computed(() => !!(props.turnSteps && props.turnSteps.length > 0))
const hasContent = computed(() => !!props.assistantMessage.content || props.isStreaming)

/* ── Step helpers ── */
function stepIcon(name: string): string {
  if (name === 'web_search') return '🔍'
  if (name === 'web_fetch') return '📄'
  return '🔧'
}

function toolLabel(tc: ToolCallInfo): string {
  if (tc.name === 'web_search') {
    if (tc.status === 'calling') return '正在搜索…'
    if (tc.status === 'done' && tc.result) {
      try {
        const parsed = JSON.parse(tc.result)
        const count = parsed.results?.length || 0
        return `搜索完成，找到 ${count} 个网页`
      } catch { return '搜索完成' }
    }
    return '搜索中…'
  }
  if (tc.name === 'web_fetch') {
    if (tc.status === 'calling') return '正在查看网页'
    if (tc.status === 'done') {
      const url = String(tc.args?.url || '')
      return `浏览了网页：${url.slice(0, 50)}${url.length > 50 ? '…' : ''}`
    }
    return '查看网页中…'
  }
  return tc.name
}

function handleSelectDetail(item: { title: string; url: string; content: string }) {
  emit('select-detail', item)
}

function handleToolClick(tc: ToolCallInfo) {
  if (tc.name === 'web_search' && tc.status === 'done' && tc.result) {
    try {
      const parsed = JSON.parse(tc.result)
      const results = parsed.results || []
      emit('show-search-results', results)
    } catch { /* ignore */ }
  }
}

/* ── Legacy thinking (fallback for history) ── */
const thinkDuration = ref<number>(0)
const thinkStartTime = ref<number>(0)
const thinkAutoClosed = ref(false)

const combinedThinking = computed(() => {
  const parts: string[] = []
  if (props.agentThinkingText) parts.push(props.agentThinkingText)
  if (!props.agentThinkingText && props.thinkingText) {
    try {
      const obj = JSON.parse(props.thinkingText)
      if (obj.reasoning) parts.push(obj.reasoning)
    } catch {
      parts.push(props.thinkingText)
    }
  }
  return parts.join('\n')
})

watch(() => combinedThinking.value, (val, old) => {
  if (val && !old) {
    thinkStartTime.value = Date.now()
    thinkOpen.value = true
    thinkAutoClosed.value = false
  }
})

watch(() => props.isStreaming, (val) => {
  if (!val && combinedThinking.value && thinkStartTime.value > 0) {
    thinkDuration.value = Math.round((Date.now() - thinkStartTime.value) / 1000)
    if (thinkOpen.value && !thinkAutoClosed.value) {
      setTimeout(() => { thinkOpen.value = false; thinkAutoClosed.value = true }, 2000)
    }
  }
})

function legacyThinkLabel(): string {
  if (props.isStreaming && !props.assistantMessage.content) return '正在思考…'
  const s = thinkDuration.value
  if (s > 0) return `思考过程（${s}s）`
  return '思考过程'
}

const hasLegacyThinking = computed(() => !!combinedThinking.value)
const hasLegacyToolCalls = computed(() => props.toolCalls && props.toolCalls.length > 0)
const hasLegacySteps = computed(() => hasLegacyThinking.value || hasLegacyToolCalls.value)
</script>

<template>
  <div class="assistant-turn">
    <!-- ── Step-by-step mode: single loop over turnSteps in original order ── -->
    <template v-if="hasTurnSteps">
      <div class="think-section">
        <div class="think-header" @click="thinkOpen = !thinkOpen">
          <span class="think-header-icon">☁️</span>
          <span class="think-header-label">{{ props.isStreaming ? '正在思考…' : '思考过程' }}</span>
          <span class="think-toggle">{{ thinkOpen ? '收起' : '展开' }}</span>
        </div>
        <div v-if="thinkOpen" class="think-body">
          <!-- Single loop preserves chronological order -->
          <template v-for="step in turnSteps" :key="step.id">
            <!-- Thinking block -->
            <div v-if="step.type === 'thinking'" class="think-block">
              {{ step.thinking }}
            </div>
            <!-- Search result — click opens right panel -->
            <div v-else-if="step.type === 'tool_call' && step.toolCall && step.toolCall.name === 'web_search'" class="tool-step">
              <div
                class="tool-step-header clickable"
                @click="handleToolClick(step.toolCall!)"
              >
                <span class="tool-step-icon">{{ stepIcon(step.toolCall.name) }}</span>
                <span class="tool-step-label">{{ toolLabel(step.toolCall) }}</span>
                <span v-if="step.toolCall.status === 'done'" class="tool-step-toggle">查看 →</span>
                <span v-else-if="step.toolCall.status === 'calling'" class="tool-step-spinner" />
              </div>
            </div>
            <!-- Other tool calls — label only, no expand -->
            <div v-else-if="step.type === 'tool_call' && step.toolCall" class="tool-step">
              <div class="tool-step-header">
                <span class="tool-step-icon">{{ stepIcon(step.toolCall.name) }}</span>
                <span class="tool-step-label">{{ toolLabel(step.toolCall) }}</span>
                <span v-if="step.toolCall.status === 'calling'" class="tool-step-spinner" />
              </div>
            </div>
          </template>
        </div>
      </div>
    </template>

    <!-- ── Legacy mode ── -->
    <template v-else>
      <div v-if="hasLegacySteps" class="think-section">
        <div class="think-header" @click="thinkOpen = !thinkOpen">
          <span class="think-header-icon">☁️</span>
          <span class="think-header-label">{{ legacyThinkLabel() }}</span>
          <span class="think-toggle">{{ thinkOpen ? '收起' : '展开' }}</span>
        </div>
        <div v-if="thinkOpen" class="think-body">
          <!-- Legacy thinking -->
          <div v-if="hasLegacyThinking" class="think-block">{{ combinedThinking }}</div>
          <!-- Legacy tool calls (inside collapse) -->
          <div v-for="tc in toolCalls" :key="tc.id" class="tool-step">
            <!-- Search result — click opens right panel -->
            <template v-if="tc.name === 'web_search'">
              <div class="tool-step-header clickable" @click="handleToolClick(tc)">
                <span class="tool-step-icon">{{ stepIcon(tc.name) }}</span>
                <span class="tool-step-label">{{ toolLabel(tc) }}</span>
                <span v-if="tc.status === 'done'" class="tool-step-toggle">查看 →</span>
                <span v-else-if="tc.status === 'calling'" class="tool-step-spinner" />
              </div>
            </template>
            <!-- Other tool calls — label only, no expand -->
            <template v-else>
              <div class="tool-step-header">
                <span class="tool-step-icon">{{ stepIcon(tc.name) }}</span>
                <span class="tool-step-label">{{ toolLabel(tc) }}</span>
                <span v-if="tc.status === 'calling'" class="tool-step-spinner" />
              </div>
            </template>
          </div>
        </div>
      </div>
    </template>

    <!-- ── Answer (always outside collapse) ── -->
    <div v-if="hasContent" class="answer-step">
      <MessageItem
        :message="assistantMessage"
        :teacher-label="teacherLabel"
        :in-turn="true"
      />
    </div>

    <div v-if="isStreaming && !hasContent && !hasTurnSteps && !hasLegacySteps" class="streaming-hint">
      思考中…
    </div>
  </div>
</template>

<style scoped>
.assistant-turn {
  margin: 0;
}

/* ── Thinking section ── */
.think-section {
  margin-bottom: 16px;
}

.think-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  cursor: pointer;
  user-select: none;
}

.think-header-icon { font-size: 15px; color: var(--text-tertiary); flex-shrink: 0; }
.think-header-label { font-size: 14px; color: var(--text-secondary); }

.think-toggle {
  margin-left: auto;
  font-size: 12px;
  color: var(--accent);
  flex-shrink: 0;
}

.think-body {
  padding: 0 0 0 23px;
}

.think-block {
  margin: 0;
  padding: 6px 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-tertiary);
  white-space: pre-wrap;
  word-break: break-word;
}
.think-block + .think-block,
.think-block + .tool-step {
  margin-top: 8px;
}

/* ── Tool call steps (inside think-body) ── */
.tool-step {
  margin: 0;
}
.tool-step + .tool-step,
.tool-step + .think-block {
  margin-top: 8px;
}

.tool-step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
  font-size: 14px;
}
.tool-step-header.clickable {
  cursor: pointer;
}

.tool-step-icon { font-size: 15px; color: var(--text-tertiary); flex-shrink: 0; }
.tool-step-label { color: var(--text-secondary); font-weight: 400; }

.tool-step-toggle {
  margin-left: auto;
  font-size: 12px;
  color: var(--accent);
  flex-shrink: 0;
}

.tool-step-spinner {
  width: 12px; height: 12px;
  border: 2px solid var(--border-default);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-left: auto;
}
@keyframes spin { to { transform: rotate(360deg); } }

.tool-step-body {
  padding: 4px 0 0 0;
}

/* ── Answer ── */
.answer-step {
  margin-top: 4px;
}

/* ── Streaming hint ── */
.streaming-hint {
  font-size: 13px;
  color: var(--accent);
  animation: pulse 1.5s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
}
</style>
