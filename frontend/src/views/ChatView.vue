<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { AGENT_MAP, type Thread, type Message } from '@/types/api'
import { useChatStream, type ChatMessage } from '@/composables/useChatStream'
import ChatSidebar from '@/components/ChatSidebar.vue'
import ChatHeader from '@/components/ChatHeader.vue'
import MessageList from '@/components/MessageList.vue'
import ChatInput from '@/components/ChatInput.vue'
import FollowupSuggestions from '@/components/FollowupSuggestions.vue'
import DetailPanel from '@/components/DetailPanel.vue'

const route = useRoute()
const router = useRouter()

const teacherId = ref(localStorage.getItem('teacherId') || 'anonymous')
const messages = ref<ChatMessage[]>([])
const threads = ref<Thread[]>([])
const threadTitle = ref('')
const deepThinking = ref(false)

/* ── Detail panel ── */
const detailVisible = ref(false)
const detailItem = ref<{ title: string; url: string; content: string } | null>(null)

function handleSelectDetail(item: { title: string; url: string; content: string }) {
  detailItem.value = item
  detailVisible.value = true
}

function handleCloseDetail() {
  detailVisible.value = false
}

const chatRef = ref<HTMLElement>()
const hasMore = ref(false)
const loadingMore = ref(false)

const { isStreaming, activeAgent, currentThreadId, toolCalls, thinkingText, agentThinkingText, turnSteps, createThread, deleteThread, loadThreads, loadMessages, send, stop } =
  useChatStream({ teacherId, messages })

function saveTeacherId(v: string) {
  teacherId.value = v
  localStorage.setItem('teacherId', v)
}

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

async function newThread() {
  try {
    const t = await createThread()
    currentThreadId.value = t.id
    router.push(`/chat/${t.id}`)
  } catch {
    // Retry once
    try {
      const t = await createThread()
      currentThreadId.value = t.id
      router.push(`/chat/${t.id}`)
    } catch {
      return // silent fail — user can try again
    }
  }
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

async function handleSend(content: string) {
  // Thread is always created server-side before first message
  if (!currentThreadId.value) {
    try {
      const t = await createThread()
      currentThreadId.value = t.id
      router.replace(`/chat/${t.id}`)
    } catch {
      // Retry once
      try {
        const t = await createThread()
        currentThreadId.value = t.id
        router.replace(`/chat/${t.id}`)
      } catch {
        // Show error as system message instead of silent random-id fallback
        messages.value.push({
          id: 'error-' + Date.now(),
          role: 'assistant',
          content: '创建会话失败，请检查网络后刷新页面重试'
        })
        return
      }
    }
  }

  await send(currentThreadId.value, content, deepThinking.value, (_agent) => {
    scrollDown()
  })

  await refreshThreads()
  const t = threads.value.find(t => t.id === currentThreadId.value)
  if (t) threadTitle.value = t.title || ''
  scrollDown()
}

/* ── Quick prompt from sidebar agent ── */

function handleQuickPrompt(text: string) {
  handleSend(text)
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
    // Keep scroll position stable after prepending
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
  } else if (route.path === '/') {
    // welcome — no thread loaded
  }
})

/* ── Watch for route changes ── */
import { watch } from 'vue'
watch(() => route.params.threadId, async (newId) => {
  if (newId && typeof newId === 'string' && newId !== currentThreadId.value) {
    await openThread(newId)
  }
})
</script>

<template>
  <div class="app-layout">
    <ChatSidebar
      :threads="threads"
      :active-thread-id="currentThreadId"
      :active-agent="activeAgent"
      :teacher-id="teacherId"
      @update:teacher-id="saveTeacherId"
      @select-thread="selectThread"
      @new-thread="newThread"
      @quick-prompt="handleQuickPrompt"
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
            <!-- Welcome -->
            <div v-if="!currentThreadId && messages.length === 0" class="welcome">
              <div class="welcome-icon">🏛️</div>
              <h2>你好，辅导员</h2>
              <p>选择左侧 AI 助理或直接输入问题，我会把任务路由到最合适的 Agent 处理。</p>
              <div class="quick-row">
                <el-button
                  v-for="a in ['帮我写一份学风建设主题班会提纲','学生出现心理危机怎么应对','帮我起草一份评奖评优通知','帮我分析这个学生的就业方向']"
                  :key="a" size="small" round @click="handleSend(a)"
                >{{ a }}</el-button>
              </div>
            </div>

            <MessageList
              :messages="messages"
              :teacher-label="teacherId"
              :tool-calls="toolCalls"
              :thinking-text="thinkingText"
              :agent-thinking-text="agentThinkingText"
              :turn-steps="turnSteps"
              :is-streaming="isStreaming"
              @select-detail="handleSelectDetail"
            />
          </div>

          <FollowupSuggestions
            :thread-id="currentThreadId"
            :is-streaming="isStreaming"
            @select="handleSend"
          />

          <ChatInput
            :is-streaming="isStreaming"
            v-model="deepThinking"
            @send="handleSend"
            @stop="stop"
          />
        </div>

        <DetailPanel
          :visible="detailVisible"
          :item="detailItem"
          @close="handleCloseDetail"
        />
      </div>
    </main>
  </div>
</template>

<style scoped>
.app-layout { display: flex; height: 100vh; background: #f5f0e8; font-family: "PingFang SC", system-ui, sans-serif; }

.main-area { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.main-content { flex: 1; display: flex; min-height: 0; }
.chat-column { flex: 1; display: flex; flex-direction: column; min-width: 0; }
.chat-area { flex: 1; overflow-y: auto; padding: 20px 24px; }

.welcome { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; text-align: center; }
.welcome-icon { font-size: 52px; margin-bottom: 12px; }
.welcome h2 { font-size: 22px; font-weight: 700; margin-bottom: 6px; }
.welcome p { font-size: 14px; color: #6b5e4a; max-width: 400px; line-height: 1.6; margin-bottom: 20px; }
.quick-row { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
</style>
