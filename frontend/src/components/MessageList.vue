<script setup lang="ts">
import { computed } from 'vue'
import type { ChatMessage, ToolCallInfo, TurnStep } from '@/composables/useChatStream'
import MessageItem from './MessageItem.vue'
import AssistantTurn from './AssistantTurn.vue'

const props = defineProps<{
  messages: ChatMessage[]
  teacherLabel?: string
  toolCalls?: ToolCallInfo[]
  thinkingText?: string
  agentThinkingText?: string
  turnSteps?: TurnStep[]
  isStreaming?: boolean
}>()

const emit = defineEmits<{
  'select-detail': [item: { title: string; url: string; content: string }]
}>()

// Find the last assistant message index — this is the one to wrap in AssistantTurn
const lastAssistantIndex = computed(() => {
  for (let i = props.messages.length - 1; i >= 0; i--) {
    if (props.messages[i]?.role === 'assistant') return i
  }
  return -1
})

const hasActiveTurn = computed(() => {
  return !!(props.isStreaming || props.thinkingText || props.agentThinkingText || (props.toolCalls && props.toolCalls.length > 0))
})
</script>

<template>
  <div class="msg-list">
    <div v-if="messages.length === 0 && !hasActiveTurn" class="empty-hint">
      <slot name="empty" />
    </div>

    <template v-for="(m, idx) in messages" :key="m.id">
      <!-- User messages always render directly -->
      <MessageItem
        v-if="m.role === 'user'"
        :message="m"
        :teacher-label="teacherLabel"
      />

      <!-- Last assistant message: wrap in AssistantTurn -->
      <AssistantTurn
        v-else-if="m.role === 'assistant' && idx === lastAssistantIndex"
        :thinking-text="thinkingText ?? ''"
        :agent-thinking-text="agentThinkingText ?? ''"
        :tool-calls="toolCalls ?? []"
        :turn-steps="turnSteps ?? []"
        :assistant-message="m"
        :is-streaming="isStreaming ?? false"
        :teacher-label="teacherLabel"
        @select-detail="(item) => emit('select-detail', item)"
      />

      <!-- Historical assistant message with thinking -->
      <AssistantTurn
        v-else-if="m.role === 'assistant' && m.thinking"
        :thinking-text="m.thinking"
        :agent-thinking-text="''"
        :tool-calls="[]"
        :assistant-message="m"
        :is-streaming="false"
        :teacher-label="teacherLabel"
        @select-detail="(item) => emit('select-detail', item)"
      />

      <!-- Historical assistant messages without thinking -->
      <MessageItem
        v-else-if="m.role === 'assistant'"
        :message="m"
        :teacher-label="teacherLabel"
      />
    </template>

    <!-- Edge case: streaming with thinking/toolCalls but no assistant message yet -->
    <AssistantTurn
      v-if="hasActiveTurn && lastAssistantIndex < 0"
      :thinking-text="thinkingText ?? ''"
      :agent-thinking-text="agentThinkingText ?? ''"
      :tool-calls="toolCalls ?? []"
      :turn-steps="turnSteps ?? []"
      :assistant-message="{ id: '', role: 'assistant', content: '' }"
      :is-streaming="isStreaming ?? false"
      :teacher-label="teacherLabel"
      @select-detail="(item) => emit('select-detail', item)"
    />
  </div>
</template>

<style scoped>
.msg-list {
  max-width: 800px; margin: 0 auto;
  display: flex; flex-direction: column; gap: 14px;
}
.empty-hint {
  display: flex; justify-content: center; padding: 40px 0;
}
</style>
