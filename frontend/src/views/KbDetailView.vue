<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as docApi from '@/api/doc'
import * as kbApi from '@/api/kb'
import type { KnowledgeBase, Document } from '@/types'

const route = useRoute()
const router = useRouter()
const kbId = Number(route.params.id)
const kb = ref<KnowledgeBase | null>(null)
const docs = ref<Document[]>([])
const loading = ref(false)
const uploading = ref(false)
const dragOver = ref(false)

async function fetchKb() {
  const res = await kbApi.listKb()
  if (res.data.code === 200) {
    kb.value = res.data.data.find((k: KnowledgeBase) => k.id === kbId) || null
  }
}

async function fetchDocs() {
  loading.value = true
  try {
    const res = await docApi.listDocs(kbId)
    if (res.data.code === 200) docs.value = res.data.data
  } finally {
    loading.value = false
  }
}

async function handleUpload(file: File) {
  uploading.value = true
  try {
    await docApi.uploadDoc(kbId, file)
    fetchDocs()
  } catch (e: any) {
    alert('上传失败: ' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    uploading.value = false
  }
}

function onFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files?.length) handleUpload(input.files[0])
}

function onDrop(e: DragEvent) {
  dragOver.value = false
  if (e.dataTransfer?.files.length) handleUpload(e.dataTransfer.files[0])
}

function triggerUpload() {
  if (!uploading.value) {
    const el = document.getElementById('fileInput') as HTMLInputElement | null
    el?.click()
  }
}

async function deleteDoc(id: number) {
  if (!confirm('确定删除此文档？删除后不可恢复。')) return
  try {
    const res = await docApi.deleteDoc(id)
    if (res.data.code === 200) fetchDocs()
    else alert('删除失败: ' + (res.data.message || '未知错误'))
  } catch {
    alert('网络错误，无法删除')
  }
}

const statusLabel: Record<number, string> = { 0: '待处理', 1: '处理中', 2: '已完成', 3: '失败' }
const statusTag: Record<number, string> = { 0: 'tag-pending', 1: 'tag-processing', 2: 'tag-done', 3: 'tag-failed' }

onMounted(() => { fetchKb(); fetchDocs() })
</script>

<template>
  <div v-for="i in 10" :key="i" class="ink-particle"></div>

  <div class="app-layout">
    <main class="main-content">
      <div class="page-header">
        <div style="display:flex;align-items:center;gap:12px">
          <a class="btn-back" @click="router.push('/kb')">&larr;</a>
          <h2>{{ kb?.name || '知识库名称' }}</h2>
        </div>
        <RouterLink :to="`/chat/${kbId}`" class="btn btn-primary">开始问答</RouterLink>
      </div>

      <div
        class="upload-area"
        :class="{ 'drag-over': dragOver }"
        @dragover.prevent="dragOver = true"
        @dragleave="dragOver = false"
        @drop.prevent="onDrop"
        @click="triggerUpload"
      >
        <template v-if="!uploading">
          <p>拖拽文件到此处或点击上传</p>
          <p style="font-size:12px;color:var(--ink-pale);margin-bottom:0">支持 PDF、TXT、MD、DOCX、PPTX、HTML、CSV 格式</p>
        </template>
        <p v-else>上传中...</p>
      </div>
      <form style="display:none">
        <input type="file" id="fileInput" accept=".pdf,.txt,.md,.docx,.pptx,.html,.htm,.csv" @change="onFileSelected" />
      </form>

      <div v-if="loading" class="loading">加载中...</div>
      <div v-else-if="docs.length === 0" class="empty-state" style="display:block">
        <p>还没有文档</p>
        <p style="margin-top:8px;font-size:13px;color:var(--ink-pale)">上传文档文件开始构建知识库</p>
      </div>
      <div v-else class="doc-list">
        <div v-for="doc in docs" :key="doc.id" class="doc-item">
          <div class="doc-info">
            <span class="doc-name">{{ doc.originalName || doc.filename || '未知文件' }}</span>
            <span v-if="doc.chunkCount" class="doc-chunks">{{ doc.chunkCount }} 段</span>
          </div>
          <span :class="['tag', statusTag[doc.status] || 'tag-pending']">{{ statusLabel[doc.status] || doc.status }}</span>
          <button class="btn btn-sm btn-danger" @click="deleteDoc(doc.id)">删除</button>
        </div>
      </div>
    </main>
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
.btn-back { color: var(--ink-muted); font-size: 14px; cursor: pointer; }
.btn-back:hover { color: var(--cinnabar); }
.upload-area {
  border: 2px dashed var(--border);
  border-radius: var(--radius-lg);
  padding: 48px 32px;
  text-align: center;
  background: rgba(255,252,247,0.5);
  margin-bottom: 24px;
  transition: all 0.25s;
  cursor: pointer;
  backdrop-filter: blur(4px);
}
.upload-area:hover, .drag-over {
  border-color: var(--cinnabar);
  background: var(--cinnabar-dim);
  transform: translateY(-1px);
}
.upload-area p { color: var(--ink-muted); margin-bottom: 12px; font-size: 14px; }
.doc-list { display: flex; flex-direction: column; gap: 0; }
.doc-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  background: rgba(255,252,247,0.7);
  border-radius: var(--radius);
  margin-bottom: 8px;
  border: 1px solid var(--border-lt);
  transition: all 0.2s;
}
.doc-item:hover {
  border-color: var(--border);
  box-shadow: var(--shadow);
  transform: translateX(2px);
}
.doc-info { display: flex; align-items: center; gap: 12px; min-width: 0; flex: 1; }
.doc-name { font-size: 14px; font-weight: 600; color: var(--ink); }
.doc-chunks { font-size: 12px; color: var(--ink-pale); flex-shrink: 0; }
</style>
