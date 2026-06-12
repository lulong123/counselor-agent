<script setup lang="ts">
import { ref, watch } from 'vue'
import { Promotion, Close } from '@element-plus/icons-vue'

const props = defineProps<{
  isStreaming: boolean
  disabled?: boolean
  prefill?: string
}>()

const emit = defineEmits<{
  send: [content: string]
  stop: []
  'update:prefill': [value: string]
}>()

const input = ref('')

watch(() => props.prefill, (val) => {
  if (val) {
    input.value = val
    emit('update:prefill', '')
  }
})

function handleSend() {
  const text = input.value.trim()
  if (!text || props.isStreaming) return
  input.value = ''
  emit('send', text)
}
</script>

<template>
  <div class="composer">
    <div class="composer-inner">
      <el-input
        v-model="input"
        placeholder="输入问题，Enter 发送…"
        type="textarea"
        :rows="4"
        :autosize="{ minRows: 4, maxRows: 8 }"
        :disabled="disabled"
        class="input-area"
        @keydown.enter.exact.prevent="handleSend()"
      />

      <button
        v-if="!isStreaming"
        class="send-btn"
        :disabled="!input.trim() || disabled"
        @click="handleSend()"
        title="发送"
      >
        <el-icon size="18"><Promotion /></el-icon>
      </button>
      <button
        v-else
        class="send-btn stop-btn"
        @click="emit('stop')"
        title="停止"
      >
        <el-icon size="18"><Close /></el-icon>
      </button>
    </div>
  </div>
</template>

<style scoped>
.composer {
  display: flex;
  justify-content: center;
  width: 100%;
  padding: 0 0 8px;
}

.composer-inner {
  position: relative;
  max-width: 910px;
  width: 100%;
}

.input-area {
  width: 100%;
}

.input-area :deep(.el-textarea__inner) {
  border-radius: var(--radius-lg);
  border: 1.5px solid var(--border-default);
  font-size: 15px;
  padding: 16px 52px 16px 20px;
  line-height: 1.6;
  background: var(--bg-surface);
  transition: border-color 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out);
  resize: none;
  color: var(--text-primary);
  font-family: inherit;
  box-shadow: var(--shadow-xs);
}

.input-area :deep(.el-textarea__inner:focus) {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(200, 136, 26, 0.12);
  outline: none;
}

.input-area :deep(.el-textarea__inner::placeholder) {
  color: var(--text-tertiary);
}

.send-btn {
  position: absolute;
  right: 8px;
  bottom: 8px;
  width: 36px; height: 36px;
  display: grid; place-items: center;
  border-radius: 50%;
  border: none;
  background: var(--accent);
  color: #fff;
  cursor: pointer;
  transition: all 0.15s var(--ease-out);
}
.send-btn:hover:not(:disabled) {
  background: var(--accent-hover);
  transform: scale(1.05);
}
.send-btn:active:not(:disabled) {
  transform: scale(0.95);
}
.send-btn:disabled {
  background: #d1d5db;
  cursor: not-allowed;
}

.stop-btn {
  background: #ef4444;
}
.stop-btn:hover {
  background: #dc2626;
}
</style>
