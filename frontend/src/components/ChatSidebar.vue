<script setup lang="ts">
import type { Thread } from '@/types/api'
import { ChatDotRound } from '@element-plus/icons-vue'

defineProps<{
  threads: Thread[]
  activeThreadId?: string | null
}>()

const emit = defineEmits<{
  'select-thread': [threadId: string]
  'new-thread': []
  'delete-thread': [threadId: string]
}>()

function onDeleteThread(threadId: string, event: Event) {
  event.stopPropagation()
  emit('delete-thread', threadId)
}

function formatDate(dateStr?: string) {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 86400000) {
    return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return dateStr.slice(5, 10)
}
</script>

<template>
  <aside class="sidebar">
    <!-- Brand -->
    <div class="sidebar-brand">
      <span class="brand-text">高校辅导员助手</span>
    </div>

    <!-- New conversation -->
    <button class="new-chat-btn" @click="emit('new-thread')">
      <el-icon size="16"><ChatDotRound /></el-icon>
      <span>新对话</span>
    </button>

    <!-- Thread list -->
    <div class="sidebar-section">
      <span class="section-label">历史对话</span>

      <div v-if="threads.length" class="thread-list">
        <div
          v-for="t in threads"
          :key="t.id"
          class="thread-item"
          :class="{ active: t.id === activeThreadId }"
          @click="emit('select-thread', t.id)"
        >
          <div class="thread-main">
            <span class="thread-title">{{ t.title || '新对话' }}</span>
            <span class="thread-time">{{ formatDate(t.updatedAt) }}</span>
          </div>
          <button
            class="thread-delete"
            title="删除对话"
            @click="(e) => onDeleteThread(t.id, e)"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M3 6h18M8 6V4a2 2 0 012-2h4a2 2 0 012 2v2m3 0v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6h14"/></svg>
          </button>
        </div>
      </div>

      <div v-else class="thread-empty">
        <p class="empty-text">暂无对话</p>
        <p class="empty-hint">点击上方开始新对话</p>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 270px;
  flex-shrink: 0;
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-default);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  user-select: none;
}

/* ── Brand ── */
.sidebar-brand {
  display: flex;
  align-items: center;
  padding: 20px 20px 16px;
}
.brand-text {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.3px;
}

/* ── New chat ── */
.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 0 16px 20px;
  padding: 9px 0;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-default);
  background: var(--bg-surface);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s var(--ease-out);
  font-family: inherit;
}
.new-chat-btn:hover {
  border-color: var(--accent);
  background: var(--accent-soft);
}

/* ── Section label ── */
.sidebar-section {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.section-label {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.6px;
  color: var(--text-tertiary);
  padding: 0 20px 8px;
}

/* ── Thread list ── */
.thread-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.thread-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all 0.12s var(--ease-out);
}
.thread-item:hover {
  background: var(--bg-sidebar-hover);
}
.thread-item.active {
  background: var(--bg-sidebar-active);
}
.thread-item.active .thread-title {
  color: var(--text-primary);
}

.thread-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.thread-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.thread-time {
  font-size: 11px;
  color: var(--text-tertiary);
}

.thread-delete {
  display: none;
  margin-left: 6px;
  padding: 4px;
  border: none;
  background: none;
  color: var(--text-tertiary);
  cursor: pointer;
  border-radius: 4px;
  transition: all 0.12s;
  flex-shrink: 0;
  line-height: 0;
}
.thread-item:hover .thread-delete {
  display: block;
}
.thread-delete:hover {
  background: #fdd;
  color: #c33;
}

/* ── Empty ── */
.thread-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 20px;
}
.empty-text {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
}
.empty-hint {
  font-size: 11px;
  color: #c4b99a;
  margin: 0;
}
</style>
