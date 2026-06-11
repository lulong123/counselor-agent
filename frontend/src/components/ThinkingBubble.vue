<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  text: string
  isStreaming: boolean
}>()

const isOpen = ref(true)
const hasAutoClosed = ref(false)

// Auto-open when new thinking starts
watch(() => props.text, (val, old) => {
  if (val && !old) {
    isOpen.value = true
    hasAutoClosed.value = false
  }
})

// Auto-close after streaming stops (DeerFlow pattern: 1s delay)
watch(() => props.isStreaming, (val) => {
  if (!val && props.text && isOpen.value && !hasAutoClosed.value) {
    setTimeout(() => {
      isOpen.value = false
      hasAutoClosed.value = true
    }, 1000)
  }
})

function summaryText(): string {
  try {
    const obj = JSON.parse(props.text)
    const parts: string[] = []
    if (obj.agentId && obj.agentId !== 'null') parts.push(`Agent: ${obj.agentId}`)
    if (obj.risk) parts.push(`风险: ${obj.risk}`)
    if (obj.department) parts.push(`领域: ${obj.department}`)
    return parts.length > 0 ? parts.join(' · ') : '正在分析需求…'
  } catch {
    const t = props.text
    return t.length > 60 ? t.slice(0, 60) + '…' : t
  }
}
</script>

<template>
  <div v-if="text" class="thinking">
    <div class="thinking-trigger" @click="isOpen = !isOpen">
      <span class="thinking-icon">🧠</span>
      <span class="thinking-summary">{{ summaryText() }}</span>
      <span class="thinking-chevron" :class="{ open: isOpen }">▸</span>
    </div>
    <div v-show="isOpen" class="thinking-content">
      <pre>{{ text }}</pre>
    </div>
  </div>
</template>

<style scoped>
.thinking {
  max-width: 800px; margin: 0 auto 4px;
  border-left: 2px solid #d4c08b;
  padding-left: 10px;
}

.thinking-trigger {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 0; cursor: pointer; user-select: none;
  color: #8b7355; font-size: 13px;
  transition: color 0.2s;
}
.thinking-trigger:hover { color: #5a4a2f; }

.thinking-icon { font-size: 14px; flex-shrink: 0; }
.thinking-summary { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.thinking-chevron {
  font-size: 10px; transition: transform 0.2s; flex-shrink: 0;
}
.thinking-chevron.open { transform: rotate(90deg); }

.thinking-content {
  padding: 6px 0 8px;
  animation: thinkIn 0.2s ease;
}
.thinking-content pre {
  margin: 0; padding: 8px 10px;
  background: #faf8f3; border-radius: 6px;
  font-size: 11px; line-height: 1.5;
  color: #7a6e5c; white-space: pre-wrap; word-break: break-word;
  max-height: 200px; overflow-y: auto;
  font-family: 'SF Mono', 'Menlo', monospace;
}

@keyframes thinkIn {
  from { opacity: 0; max-height: 0; }
  to { opacity: 1; max-height: 200px; }
}
</style>
