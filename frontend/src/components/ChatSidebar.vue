<script setup lang="ts">
import { AGENTS, type Agent, type Thread } from '@/types/api'

defineProps<{
  threads: Thread[]
  activeThreadId?: string | null
  activeAgent?: Agent | null
  teacherId: string
}>()

const emit = defineEmits<{
  'update:teacherId': [value: string]
  'select-thread': [threadId: string]
  'new-thread': []
  'quick-prompt': [text: string]
  'delete-thread': [threadId: string]
}>()

function onDeleteThread(threadId: string, event: Event) {
  event.stopPropagation()
  emit('delete-thread', threadId)
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <span class="brand-icon">枢</span>
      <div>
        <h1>枢衡 Shuheng</h1>
        <p class="brand-sub">辅导员 AI 工作台</p>
      </div>
    </div>

    <el-input
      :model-value="teacherId"
      placeholder="辅导员编号"
      size="small"
      @update:model-value="(v: string) => emit('update:teacherId', v)"
    />

    <div>
      <p class="section-title">AI 助理</p>
      <div class="agent-grid">
        <div
          v-for="a in AGENTS" :key="a.id"
          class="agent-card"
          :class="{ active: activeAgent?.id === a.id }"
          @click="emit('quick-prompt', `帮${a.desc.slice(2, 18)}…`)"
        >
          <span class="agent-icon">{{ a.icon }}</span>
          <div>
            <div class="agent-name">{{ a.name }}</div>
            <div class="agent-duty">{{ a.duty }}</div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="threads.length" class="thread-section">
      <div class="thread-section-header">
        <p class="section-title">最近对话</p>
        <el-button size="small" text type="primary" @click="emit('new-thread')">+ 新建</el-button>
      </div>
      <div
        v-for="t in threads"
        :key="t.id"
        class="thread-item"
        :class="{ active: t.id === activeThreadId }"
        @click="emit('select-thread', t.id)"
      >
        <span class="thread-title-text">{{ t.title || '新对话' }}</span>
        <span class="thread-time">{{ t.updatedAt?.slice(5, 10) }}</span>
        <button class="thread-delete-btn" title="删除对话" @click="(e) => onDeleteThread(t.id, e)">×</button>
      </div>
    </div>

    <div v-else class="thread-section">
      <p class="section-title">对话</p>
      <el-button size="small" type="primary" plain @click="emit('new-thread')">开始新对话</el-button>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 280px; flex-shrink: 0; background: #fefcf5;
  border-right: 1px solid #e8dcc8;
  display: flex; flex-direction: column; gap: 16px;
  padding: 16px; overflow-y: auto;
}
.brand { display: flex; align-items: center; gap: 12px; padding-bottom: 12px; border-bottom: 1px solid #e8dcc8; }
.brand-icon {
  width: 40px; height: 40px; display: grid; place-items: center;
  border-radius: 8px; background: linear-gradient(135deg, #c8881a, #9a6510);
  color: #fff; font-size: 20px; font-weight: 800;
}
.brand h1 { font-size: 17px; font-weight: 700; margin: 0; }
.brand-sub { font-size: 11px; color: #9b8e7a; margin: 0; }
.section-title { font-size: 11px; font-weight: 700; color: #9b8e7a; text-transform: uppercase; letter-spacing: 0.06em; margin-bottom: 6px; }
.agent-grid { display: flex; flex-direction: column; gap: 3px; }
.agent-card {
  display: flex; align-items: center; gap: 8px; padding: 7px 8px;
  border-radius: 8px; cursor: pointer; border: 1px solid transparent; transition: all 0.15s;
}
.agent-card:hover { background: rgba(200, 136, 26, 0.06); border-color: #f5e6c8; }
.agent-card.active { background: #f5e6c8; border-color: #c8881a; }
.agent-icon {
  width: 30px; height: 30px; display: grid; place-items: center;
  border-radius: 6px; background: linear-gradient(135deg, #d4a853, #b8860b);
  color: #fff; font-size: 13px; font-weight: 800; flex-shrink: 0;
}
.agent-name { font-size: 12px; font-weight: 600; line-height: 1.3; }
.agent-duty { font-size: 10px; color: #9b8e7a; line-height: 1.3; }
.thread-section { border-top: 1px solid #e8dcc8; padding-top: 12px; }
.thread-section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.thread-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 10px; border-radius: 8px; cursor: pointer; transition: all 0.15s;
}
.thread-item:hover { background: rgba(200, 136, 26, 0.04); }
.thread-item.active { background: rgba(200, 136, 26, 0.1); }
.thread-title-text { font-size: 13px; color: #4a3f2f; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.thread-time { font-size: 10px; color: #9b8e7a; margin-left: 8px; white-space: nowrap; }
.thread-delete-btn {
  display: none; margin-left: 4px; padding: 0 4px; border: none; background: none;
  color: #c88; font-size: 16px; font-weight: 700; cursor: pointer; line-height: 1;
  border-radius: 4px; transition: all 0.15s;
}
.thread-item:hover .thread-delete-btn { display: inline; }
.thread-delete-btn:hover { background: #fdd; color: #c33; }
</style>
