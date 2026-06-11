<script setup lang="ts">
import { ref } from 'vue'
import { Promotion, Close } from '@element-plus/icons-vue'

const props = defineProps<{
  isStreaming: boolean
  disabled?: boolean
  modelValue?: boolean
}>()

const emit = defineEmits<{
  send: [content: string]
  stop: []
  'update:modelValue': [value: boolean]
}>()

const input = ref('')

function toggleDeepThinking() {
  emit('update:modelValue', !props.modelValue)
}

function handleSend() {
  const text = input.value.trim()
  if (!text || props.isStreaming) return
  input.value = ''
  emit('send', text)
}
</script>

<template>
  <div class="composer">
    <div class="composer-actions">
      <el-tooltip :content="modelValue ? '深度思考已开启 (deepseek-v4-pro)' : '深度思考 (deepseek-v4-pro)'" placement="top">
        <el-button
          :type="modelValue ? 'warning' : 'default'"
          circle
          size="small"
          :disabled="isStreaming"
          @click="toggleDeepThinking()"
        >
          <span style="font-size:16px">🧠</span>
        </el-button>
      </el-tooltip>
    </div>
    <el-input
      v-model="input"
      placeholder="输入问题，Enter 发送…"
      type="textarea"
      :rows="1"
      :autosize="{ minRows: 1, maxRows: 4 }"
      :disabled="disabled"
      @keydown.enter.exact.prevent="handleSend()"
    />
    <el-button v-if="!isStreaming" type="primary" circle :disabled="!input.trim() || disabled" @click="handleSend()">
      <el-icon><Promotion /></el-icon>
    </el-button>
    <el-button v-else type="danger" circle @click="emit('stop')">
      <el-icon><Close /></el-icon>
    </el-button>
  </div>
</template>

<style scoped>
.composer {
  display: flex; align-items: flex-end; gap: 8px;
  padding: 12px 20px;
  background: rgba(255, 253, 247, 0.7);
  backdrop-filter: blur(12px);
  border-top: 1px solid #e8dcc8;
}
.composer-actions {
  display: flex; align-items: center;
}
.composer :deep(.el-textarea__inner) {
  border-radius: 14px; border: 1.5px solid #d4c5a9; font-size: 14px;
}
</style>
