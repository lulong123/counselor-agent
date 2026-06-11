<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  threadId: string | null
  isStreaming: boolean
}>()

const emit = defineEmits<{
  select: [text: string]
}>()

const suggestions = ref<string[]>([])

async function load() {
  if (!props.threadId) return
  try {
    const { default: axios } = await import('axios')
    const { data } = await axios.get(`/api/threads/${props.threadId}/suggestions`, {
      headers: { 'X-Teacher-Id': localStorage.getItem('teacherId') || 'anonymous' },
    })
    if (Array.isArray(data)) suggestions.value = data
  } catch { suggestions.value = [] }
}

watch(() => props.isStreaming, (now, was) => {
  if (was && !now) load()
})

watch(() => props.threadId, (id) => {
  if (id) load()
})

function select(text: string) {
  suggestions.value = []
  emit('select', text)
}
</script>

<template>
  <div v-if="suggestions.length" class="suggestions">
    <el-button
      v-for="(s, i) in suggestions"
      :key="i"
      size="small"
      round
      text
      @click="select(s)"
    >{{ s }}</el-button>
  </div>
</template>

<style scoped>
.suggestions {
  display: flex; flex-wrap: wrap; gap: 8px;
  padding: 8px 20px;
  background: rgba(255, 253, 247, 0.5);
}
</style>
