<script setup lang="ts">
defineProps<{
  title?: string
  agentName?: string
}>()
</script>

<template>
  <header class="chat-header">
    <h2 class="thread-title">{{ title || '新对话' }}</h2>
    <span v-if="agentName" class="agent-badge">{{ agentName }}</span>
  </header>
</template>

<style scoped>
.chat-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 28px;
  background: var(--bg-surface);
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0;
  min-height: 52px;
}

.thread-title {
  font-size: 15px;
  font-weight: 700;
  margin: 0;
  color: var(--text-primary);
  letter-spacing: -0.2px;
}

.agent-badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 3px 12px;
  border-radius: 20px;
}
</style>
