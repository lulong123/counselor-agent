<script setup lang="ts">
defineProps<{
  visible: boolean
  results: Array<{ title: string; url: string; content: string }>
}>()

defineEmits<{ close: [] }>()
</script>

<template>
  <transition name="slide-right">
    <aside v-if="visible" class="search-panel">
      <div class="panel-header">
        <span class="panel-header-title">搜索结果（{{ results.length }}）</span>
        <button class="panel-close" @click="$emit('close')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M18 6L6 18M6 6l12 12"/></svg>
        </button>
      </div>

      <div v-if="results.length" class="panel-body">
        <a
          v-for="(r, i) in results"
          :key="i"
          :href="r.url"
          target="_blank"
          rel="noopener noreferrer"
          class="result-card"
        >
          <span class="result-title">{{ r.title || r.url }}</span>
          <span class="result-url">{{ r.url }}</span>
          <span class="result-snippet">{{ r.content?.slice(0, 300) }}</span>
        </a>
      </div>

      <div v-else class="panel-empty">暂无搜索结果</div>
    </aside>
  </transition>
</template>

<style scoped>
.search-panel {
  width: 420px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  border-left: 1px solid var(--border-default);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  flex-shrink: 0;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0;
}

.panel-header-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.panel-close {
  width: 28px; height: 28px;
  display: flex; align-items: center; justify-content: center;
  border: none; background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: all 0.12s;
}
.panel-close:hover {
  background: var(--border-subtle);
  color: var(--text-primary);
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.result-card {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  text-decoration: none;
  color: inherit;
  transition: background 0.12s;
}
.result-card:hover {
  background: var(--accent-soft);
}

.result-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--accent);
  line-height: 1.4;
}
.result-card:hover .result-title {
  text-decoration: underline;
}

.result-url {
  font-size: 11px;
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-snippet {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.panel-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: var(--text-tertiary);
}

/* Slide animation */
.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.25s var(--ease-out), opacity 0.25s var(--ease-out);
}
.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>
