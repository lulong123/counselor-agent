<script setup lang="ts">
import { AGENTS } from '@/types/api'

const PROMPTS: Record<string, string> = {
  mingli:   '帮我写一份主题班会讲话稿',
  tongxin:  '帮我策划一次入党推优评选方案',
  duxue:    '帮我分析这名学生的挂科原因并给建议',
  youxu:    '帮我起草一份奖助学金评选通知',
  xinqing:  '学生最近情绪低落，我该怎么和他谈心',
  yunlan:   '帮我写一份班级群公告，提醒假期安全',
  shouwang: '学生突发状况，帮我梳理应急处理流程',
  qihang:   '帮我分析这个学生的就业方向并给简历建议',
  yanxing:  '帮我拟一个学生工作调研课题提纲',
}

const emit = defineEmits<{
  select: [prompt: string]
}>()
</script>

<template>
  <div class="agent-grid">
    <div
      v-for="(a, i) in AGENTS"
      :key="a.id"
      class="agent-card"
      :style="{ animationDelay: `${i * 40}ms` }"
      @click="emit('select', PROMPTS[a.id]!)"
    >
      <span class="agent-icon">{{ a.icon }}</span>
      <div class="agent-info">
        <span class="agent-name">{{ a.name }}</span>
        <span class="agent-duty">{{ a.duty }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agent-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  max-width: 910px;
  width: 100%;
}

.agent-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-default);
  background: var(--bg-surface);
  cursor: pointer;
  transition: all 0.2s var(--ease-out);
  box-shadow: var(--shadow-card);
  animation: card-in 0.4s var(--ease-out) both;
}
@keyframes card-in {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}

.agent-card:hover {
  border-color: var(--accent);
  box-shadow: var(--shadow-md), 0 0 0 1px var(--accent-light);
  transform: translateY(-2px);
}
.agent-card:active {
  transform: translateY(0);
}

.agent-icon {
  width: 40px; height: 40px;
  display: grid; place-items: center;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, var(--accent), var(--accent-hover));
  color: #fff;
  font-size: 17px;
  font-weight: 800;
  flex-shrink: 0;
}

.agent-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.agent-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.agent-duty {
  font-size: 11px;
  color: var(--text-tertiary);
  font-weight: 500;
}
</style>
