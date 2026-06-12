<script setup lang="ts">
import type { ChatMessage } from '@/composables/useChatStream'

defineProps<{
  message: ChatMessage
  teacherLabel?: string
  inTurn?: boolean
}>()

function renderMarkdown(text: string): string {
  let html = text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  html = html.replace(/^### (.+)$/gm, '<h4>$1</h4>')
  html = html.replace(/^## (.+)$/gm, '<h3>$1</h3>')
  html = html.replace(/^# (.+)$/gm, '<h2>$1</h2>')
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*(.+?)\*/g, '<em>$1</em>')
  html = html.replace(/^- (.+)$/gm, '<li>$1</li>')
  html = html.replace(/((?:<li>.*<\/li>\n?)+)/g, '<ul>$1</ul>')
  html = '<p>' + html.replace(/\n\n/g, '</p><p>').replace(/\n/g, '<br>') + '</p>'
  return html
}
</script>

<template>
  <div class="msg" :class="[message.role, { 'in-turn': inTurn }]">
    <span v-if="!inTurn" class="msg-avatar">
      {{ message.role === 'user' ? '辅' : (message.agent?.icon || '枢') }}
    </span>
    <div class="msg-bubble" :class="{ streaming: message.isStreaming }">
      <span v-if="message.agent && message.isStreaming" class="stream-label">
        {{ message.agent.name }} 正在生成…
      </span>
      <div v-html="renderMarkdown(message.content) || (message.isStreaming ? '' : '')" />
      <span v-if="message.isStreaming" class="cursor">▊</span>
      <span v-if="message.risk === 'HIGH'" class="risk-tag risk-high">⚠ 高风险</span>
      <span v-if="message.risk === 'MEDIUM'" class="risk-tag risk-med">⚡ 中风险</span>
    </div>
  </div>
</template>

<style scoped>
.msg {
  display: flex;
  gap: 10px;
}
.msg.in-turn { gap: 0; }
.msg.user { flex-direction: row-reverse; }

.msg-avatar {
  width: 32px; height: 32px;
  border-radius: var(--radius-sm);
  display: grid; place-items: center;
  font-size: 14px; font-weight: 800;
  color: #fff;
  flex-shrink: 0;
}
.msg.assistant .msg-avatar { background: linear-gradient(135deg, var(--accent), var(--accent-hover)); }
.msg.user .msg-avatar { background: linear-gradient(135deg, #d4a853, #b8860b); }

.msg-bubble {
  max-width: 74%;
  padding: 0 14px;
  border-radius: var(--radius-md);
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}
.msg.assistant .msg-bubble {
  background: transparent;
  border: none;
  border-top-left-radius: 4px;
  padding: 0;
}
.msg.user .msg-bubble {
  background: var(--bg-surface);
  color: var(--text-primary);
  border: 1.5px solid #b8976e;
  border-top-right-radius: 4px;
}
.msg.assistant .msg-bubble.streaming { border: none; }

.stream-label {
  display: block;
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 4px;
  font-weight: 600;
}

.cursor {
  animation: blink 1s step-end infinite;
  color: var(--accent);
}
@keyframes blink { 50% { opacity: 0; } }

.risk-tag {
  display: inline-block;
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
  margin-top: 6px;
}
.risk-high { background: #fee2e2; color: #991b1b; }
.risk-med { background: #fef3c7; color: #92400e; }
</style>
