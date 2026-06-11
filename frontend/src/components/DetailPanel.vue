<script setup lang="ts">
defineProps<{
  visible: boolean
  item: { title: string; url: string; content: string } | null
}>()

defineEmits<{ close: [] }>()
</script>

<template>
  <transition name="slide-right">
    <aside v-if="visible" class="detail-panel">
      <div class="detail-header">
        <span class="detail-header-title">详情</span>
        <button class="detail-close" @click="$emit('close')">✕</button>
      </div>
      <div v-if="item" class="detail-body">
        <h3 class="detail-title">{{ item.title || item.url }}</h3>
        <a :href="item.url" target="_blank" rel="noopener noreferrer" class="detail-url">
          {{ item.url }}
        </a>
        <div class="detail-divider" />
        <div class="detail-content">{{ item.content || '暂无详细内容' }}</div>
      </div>
      <div v-else class="detail-empty">
        暂无详情
      </div>
    </aside>
  </transition>
</template>

<style scoped>
.detail-panel {
  width: 420px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-left: 1px solid #e0e0e0;
  box-shadow: -2px 0 12px rgba(0,0,0,0.06);
  overflow: hidden;
  flex-shrink: 0;
}

.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #e9ecef;
  flex-shrink: 0;
}

.detail-header-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.detail-close {
  width: 28px; height: 28px;
  display: flex; align-items: center; justify-content: center;
  border: none; background: transparent;
  font-size: 16px; color: #999;
  cursor: pointer;
  border-radius: 6px;
}
.detail-close:hover {
  background: #f0f0f0;
  color: #333;
}

.detail-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  margin: 0 0 8px;
  line-height: 1.4;
}

.detail-url {
  font-size: 12px;
  color: #007bff;
  text-decoration: none;
  word-break: break-all;
}
.detail-url:hover { text-decoration: underline; }

.detail-divider {
  height: 1px;
  background: #e9ecef;
  margin: 14px 0;
}

.detail-content {
  font-size: 14px;
  color: #333;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-empty {
  flex: 1;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; color: #999;
}

/* ── Slide animation ── */
.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.25s ease, opacity 0.25s ease;
}
.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>
