<script setup lang="ts">
import { computed } from 'vue'
import type { ToolCallInfo } from '@/composables/useChatStream'

const props = defineProps<{
  toolCall: ToolCallInfo
  inTurn?: boolean
}>()

const emit = defineEmits<{
  'select-detail': [item: { title: string; url: string; content: string }]
}>()

function formatSearchResult(resultStr: string): Array<{ title: string; url: string; content: string }> {
  try {
    const parsed = JSON.parse(resultStr)
    return parsed.results || []
  } catch { return [] }
}

function formatFetchResult(resultStr: string): string {
  if (resultStr.startsWith('Error:')) return resultStr
  return resultStr.length > 500 ? resultStr.slice(0, 500) + '…' : resultStr
}

function openDetail(item: { title: string; url: string; content: string }) {
  emit('select-detail', item)
}
</script>

<template>
  <div class="tool-bubble" :class="[toolCall.status, { 'in-turn': inTurn }]">
    <!-- web_search -->
    <div v-if="toolCall.name === 'web_search'" class="tool-card">
      <div v-if="toolCall.status === 'done' && toolCall.result" class="tool-results">
        <div
          v-for="(r, i) in formatSearchResult(toolCall.result)"
          :key="i"
          class="result-item"
          @click="openDetail(r)"
        >
          <span class="result-title">{{ r.title || r.url }}</span>
          <span class="result-url">{{ r.url }}</span>
          <span class="result-snippet">{{ r.content?.slice(0, 150) }}</span>
        </div>
      </div>
      <div v-if="toolCall.status === 'error'" class="tool-error">搜索失败</div>
    </div>

    <!-- web_fetch -->
    <div v-else-if="toolCall.name === 'web_fetch'" class="tool-card">
      <div v-if="toolCall.status === 'done' && toolCall.result" class="tool-fetch">
        <a :href="String(toolCall.args?.url || '#')" target="_blank" rel="noopener noreferrer" class="fetch-link">
          {{ String(toolCall.args?.url || '').slice(0, 60) }}…
        </a>
        <pre class="fetch-preview">{{ formatFetchResult(toolCall.result) }}</pre>
      </div>
      <div v-if="toolCall.status === 'error'" class="tool-error">抓取失败</div>
    </div>

    <!-- other -->
    <div v-else class="tool-card">
      <div v-if="toolCall.result" class="tool-result">{{ toolCall.result }}</div>
    </div>
  </div>
</template>

<style scoped>
.tool-bubble {
  max-width: 800px; margin: 0 auto 8px;
  animation: fadeIn 0.25s ease;
}
.tool-bubble.in-turn {
  max-width: none; margin: 0;
}
@keyframes fadeIn { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; transform: translateY(0); } }

.tool-card {
  background: #f8f9fa;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 13px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}

.tool-results {
  display: flex; flex-direction: column; gap: 10px;
}
.result-item {
  display: flex; flex-direction: column; padding: 4px 0;
  text-decoration: none; color: inherit;
  border-bottom: 1px solid #e9ecef;
  cursor: pointer;
}
.result-item:last-child { border-bottom: none; }
.result-item:hover .result-title { text-decoration: underline; }
.result-title {
  font-size: 14px; font-weight: 500; color: #007bff;
  margin-bottom: 2px;
}
.result-url {
  font-size: 12px; color: #666; margin-bottom: 2px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.result-snippet {
  font-size: 12px; color: #333; line-height: 1.5;
  overflow: hidden;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}

.tool-fetch {
  /* fetch content */
}
.fetch-link {
  font-size: 12px; color: #007bff; text-decoration: none;
  display: block; margin-bottom: 6px;
}
.fetch-link:hover { text-decoration: underline; }
.fetch-preview {
  font-size: 12px; color: #333; white-space: pre-wrap; line-height: 1.5;
  max-height: 200px; overflow-y: auto; margin: 0;
  background: #fff; padding: 8px 10px; border-radius: 6px;
  border: 1px solid #e9ecef;
}

.tool-result {
  font-size: 12px; color: #333; white-space: pre-wrap; line-height: 1.5;
}

.tool-error { font-size: 12px; color: #b91c1c; }
</style>
