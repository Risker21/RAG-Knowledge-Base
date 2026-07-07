<script setup lang="ts">
import type { KnowledgeBase } from '@/types'
defineProps<{ kb: KnowledgeBase }>()
const emit = defineEmits<{ chat: []; manage: []; delete: [] }>()
</script>

<template>
  <div class="kb-card">
    <h3>{{ kb.name }}</h3>
    <p class="kb-desc">{{ kb.description || '暂无描述' }}</p>
    <p class="kb-time">{{ kb.createdAt?.slice(0, 16).replace('T', ' ') }}</p>
    <div class="kb-actions">
      <button class="btn btn-sm btn-primary" @click="emit('chat')">开始问答</button>
      <button class="btn btn-sm btn-ghost" @click="emit('manage')">管理文档</button>
      <button class="btn btn-sm btn-danger" @click="emit('delete')">删除</button>
    </div>
  </div>
</template>

<style scoped>
.kb-card {
  background: rgba(255,252,247,0.75);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: var(--radius-lg);
  padding: 28px 24px 24px;
  box-shadow: var(--shadow);
  transition: all 0.3s;
  border: 1px solid rgba(28,26,24,0.08);
  position: relative;
  cursor: pointer;
}
.kb-card::before {
  content: '';
  position: absolute;
  top: 0; left: 24px; right: 24px;
  height: 3px;
  background: linear-gradient(90deg, var(--cinnabar), var(--gold));
  border-radius: 0 0 2px 2px;
  opacity: 0;
  transition: opacity 0.3s;
}
.kb-card:hover {
  box-shadow: var(--shadow-md);
  border-color: rgba(28,26,24,0.12);
  transform: translateY(-2px);
}
.kb-card:hover::before { opacity: 1; }
.kb-card h3 {
  font-family: var(--serif);
  font-size: 16px;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 6px;
  letter-spacing: 0.5px;
}
.kb-desc {
  font-size: 13px;
  color: var(--ink-muted);
  margin-bottom: 8px;
  line-height: 1.5;
}
.kb-time {
  font-size: 12px;
  color: var(--ink-pale);
  margin-bottom: 16px;
}
.kb-actions { display: flex; gap: 8px; flex-wrap: wrap; }
</style>
