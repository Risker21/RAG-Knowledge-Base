<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import * as kbApi from '@/api/kb'
import type { KnowledgeBase } from '@/types'
import KbCard from '@/components/KbCard.vue'

const router = useRouter()
const list = ref<KnowledgeBase[]>([])
const loading = ref(false)
const showModal = ref(false)
const newName = ref('')
const newDesc = ref('')
const createError = ref('')

async function fetchList() {
  loading.value = true
  try {
    const res = await kbApi.listKb()
    if (res.data.code === 200) list.value = res.data.data
  } finally {
    loading.value = false
  }
}

async function createKb() {
  if (!newName.value.trim()) {
    createError.value = '请输入名称'
    return
  }
  createError.value = ''
  try {
    const res = await kbApi.createKb(newName.value, newDesc.value)
    if (res.data.code === 200) {
      showModal.value = false
      newName.value = ''
      newDesc.value = ''
      fetchList()
    } else {
      createError.value = res.data.message || '创建失败'
    }
  } catch {
    createError.value = '网络错误'
  }
}

async function deleteKb(id: number) {
  if (!confirm('确定删除此知识库及其中所有文档和对话记录？')) return
  try {
    const res = await kbApi.deleteKb(id)
    if (res.data.code === 200) fetchList()
    else alert('删除失败: ' + (res.data.message || '未知错误'))
  } catch {
    alert('网络错误')
  }
}

onMounted(fetchList)
</script>

<template>
  <div v-for="i in 10" :key="i" class="ink-particle"></div>

  <div class="app-layout">
    <main class="main-content">
      <div class="page-header">
        <h2>我的知识库</h2>
        <button class="btn btn-accent" @click="showModal = true">+ 新建知识库</button>
      </div>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="list.length === 0" class="empty-state">
        <p>还没有知识库</p>
        <p style="margin-top:8px;font-size:13px;color:var(--ink-pale)">点击上方「新建知识库」开始</p>
      </div>
      <div v-else class="kb-grid">
        <KbCard
          v-for="kb in list" :key="kb.id" :kb="kb"
          @chat="router.push(`/chat/${kb.id}`)"
          @manage="router.push(`/kb/${kb.id}`)"
          @delete="deleteKb(kb.id)"
        />
      </div>
    </main>
  </div>

  <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
    <div class="modal-content">
      <div class="modal-header">
        <h3>新建知识库</h3>
        <button class="modal-close" @click="showModal = false">&times;</button>
      </div>
      <div class="form-group">
        <label class="form-label">知识库名称</label>
        <input class="form-input" v-model="newName" placeholder="为你的知识库起个名字" required />
      </div>
      <div class="form-group">
        <label class="form-label">描述</label>
        <textarea class="form-input" v-model="newDesc" placeholder="描述知识库的用途（可选）" rows="3"></textarea>
      </div>
      <div v-if="createError" class="error-msg" style="color:var(--cinnabar);font-size:13px;padding:8px 12px;background:var(--cinnabar-dim);border-radius:var(--radius);margin-bottom:12px">{{ createError }}</div>
      <div style="display:flex;gap:10px;margin-top:16px">
        <button class="btn btn-primary" @click="createKb">创建</button>
        <button class="btn btn-ghost" @click="showModal = false">取消</button>
      </div>
    </div>
  </div>

  <div class="mountain-scene" aria-hidden="true">
    <svg viewBox="0 0 1440 200" preserveAspectRatio="xMidYMax meet">
      <path d="M0,200 L0,140 Q80,100 160,120 Q240,140 320,90 Q400,40 480,80 Q560,120 640,50 Q720,-10 800,30 Q880,70 960,60 Q1040,50 1120,80 Q1200,110 1280,70 Q1360,30 1440,80 L1440,200 Z" fill="currentColor"/>
      <path d="M0,200 L0,160 Q120,130 240,145 Q360,160 480,110 Q600,60 720,95 Q840,130 960,85 Q1080,40 1200,75 Q1320,110 1440,95 L1440,200 Z" fill="currentColor" opacity="0.5"/>
      <path d="M0,200 L0,180 Q180,155 360,165 Q540,175 720,140 Q900,105 1080,130 Q1260,155 1440,140 L1440,200 Z" fill="currentColor" opacity="0.25"/>
    </svg>
  </div>
</template>

<style scoped>
.app-layout { min-height: 100vh; display: flex; flex-direction: column; }
.main-content {
  padding: 32px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  flex: 1;
  position: relative;
  z-index: 1;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
  flex-wrap: wrap;
  gap: 12px;
}
.page-header h2 {
  font-family: var(--serif);
  font-size: 22px;
  font-weight: 700;
  color: var(--ink);
  letter-spacing: 1px;
}
.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}
.error-msg { border-left: 3px solid var(--cinnabar); }
</style>
