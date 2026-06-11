<script setup lang="ts">
defineProps<{
  title?: string
  agentName?: string
}>()
</script>

<template>
  <div class="chat-header">
    <h2 class="thread-title">{{ title || '新对话' }}</h2>
    <span v-if="agentName" class="agent-badge">{{ agentName }}</span>
  </div>
</template>

<style scoped>
.chat-header {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 20px;
  background: rgba(255, 253, 247, 0.85);
  border-bottom: 1px solid #e8dcc8;
  flex-shrink: 0;
}
.thread-title { font-size: 15px; font-weight: 700; margin: 0; color: #4a3f2f; }
.agent-badge {
  font-size: 11px; color: #c8881a; background: rgba(200, 136, 26, 0.1);
  padding: 2px 10px; border-radius: 12px; font-weight: 600;
}
</style>
