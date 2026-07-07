<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, computed, triggerRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chat'
import MessageBubble from '@/components/MessageBubble.vue'
import type { Reference } from '@/types'

const route = useRoute()
const router = useRouter()
const kbId = Number(route.params.kbId)
const store = useChatStore()

const input = ref('')
const sending = ref(false)
const thinking = ref(false)
const streamingContent = ref('')
const streamingRefs = ref('')
const refModalVisible = ref(false)
const refDetail = ref<{ title: string; score: number; snippet: string } | null>(null)
const localMessages = ref<any[]>(store.messages)
const streamKey = ref(0)

function updateMessages(arr: any[]) {
  localMessages.value = arr
  store.messages = arr
}

watch(() => store.messages, (val) => { localMessages.value = val }, { deep: false })

const AI_ICON = '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M12 2l2.4 7.2L22 9l-6 5.2 2.4 7.8L12 16l-6.4 6L8 14.2 2 9l7.6-.2z"/></svg>'

const activeConv = computed(() =>
  store.conversations.find(c => c.id === store.currentConvId)
)

function hasRefs(msg: any): boolean {
  return !!msg.referencesJson && msg.referencesJson !== '[]' && msg.referencesJson !== 'null'
}

function parseRefs(msg: any): Reference[] {
  try {
    return JSON.parse(typeof msg.referencesJson === 'string' ? msg.referencesJson : '[]')
  } catch { return [] }
}

function toggleRefs(e: Event) {
  const btn = e.currentTarget as HTMLElement
  const content = btn.closest('.msg-content')
  content?.classList.toggle('expanded-refs')
  btn.classList.toggle('open')
}

function extractFilename(snippet?: string): string | null {
  const m = snippet && snippet.match(/【(.+?)】/)
  return m ? m[1] : null
}

onMounted(async () => {
  await store.loadConversations(kbId)
  const convId = route.query.convId
  if (convId) {
    store.currentConvId = Number(convId)
    await store.loadMessages(store.currentConvId)
  }
})

async function newConversation() {
  await store.startConversation(kbId)
  await store.loadConversations(kbId)
}

async function switchConversation(convId: number) {
  store.currentConvId = convId
  streamingRefs.value = ''
  await store.loadMessages(convId)
}

async function deleteConv(id: number) {
  if (!confirm('确定删除此对话？')) return
  await store.removeConversation(id)
  await store.loadConversations(kbId)
}

function autoGrow(el: HTMLTextAreaElement) {
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 140) + 'px'
}

function scrollToBottom() {
  nextTick(() => {
    const el = document.querySelector('.messages')
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function handleAsk() {
  if (!input.value.trim() || sending.value) return

  const question = input.value
  let convId = store.currentConvId
  if (!convId) {
    const res = await store.startConversation(kbId, question.substring(0, 30))
    if ((res as any).code !== 200) return
    convId = store.currentConvId!
  }

  input.value = ''
  sending.value = true
  thinking.value = true
  streamingContent.value = ''
  streamingRefs.value = ''
  streamKey.value++

  // Create a reactive streaming placeholder for the AI response
  const streamMsg = reactive<any>({
    id: -Date.now(),
    conversationId: convId,
    role: 1,
    content: '',
    thinking: true,
    referencesJson: '',
    createdAt: ''
  })

  // Add user message + AI streaming placeholder to localMessages
  updateMessages([...localMessages.value, { id: Date.now(), conversationId: convId, role: 0, content: question, referencesJson: '', createdAt: '' }, streamMsg])

  let fullText = ''

  try {
    const params = new URLSearchParams({ conversationId: String(convId), kbId: String(kbId), question })
    const res = await fetch(`/chat/api/ask/stream?${params}`)
    if (!res.ok) {
      throw new Error(`请求失败 (${res.status}): ${res.statusText}`)
    }
    const reader = res.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data: ')) {
          try {
            const data = JSON.parse(trimmed.slice(6))
            if (data.type === 'token') {
              fullText += data.content
              if (thinking.value) thinking.value = false
              streamMsg.thinking = false
              streamMsg.content = fullText
              streamingContent.value = fullText
            } else if (data.type === 'done') {
              if (data.references) {
                streamMsg.referencesJson = JSON.stringify(data.references)
                streamingRefs.value = JSON.stringify(data.references)
              }
            }
          } catch { }
        }
      }
      scrollToBottom()
    }
  } catch (e: any) {
    fullText = '请求失败: ' + (e.message || '未知错误')
    streamMsg.thinking = false
    streamMsg.content = fullText
  } finally {
    sending.value = false
    thinking.value = false
    await store.loadMessages(convId)
    await store.loadConversations(kbId)
  }

  // Finalize: streamMsg is already the final message (content + refs set during streaming)
  // Don't replace it — that would change the v-for key and destroy/recreate the DOM element
  if (fullText) {
    streamingContent.value = ''
  } else {
    // Remove streaming placeholder if no response
    updateMessages(localMessages.value.filter(m => m !== streamMsg))
  }
}
</script>

<template>
  <div v-for="i in 10" :key="i" class="ink-particle"></div>

  <div class="chat-layout">
    <!-- 侧边栏 -->
    <aside class="chat-sidebar">
      <div class="sidebar-header">
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:4px">
          <span class="seal">墨</span>
          <span style="font-family:var(--serif);font-size:15px;letter-spacing:1px">墨 韵</span>
        </div>
        <button class="btn btn-accent btn-full" @click="newConversation">+ 新对话</button>
      </div>
      <div style="padding:8px 16px 4px;font-size:12px;color:var(--ink-muted);font-weight:600;letter-spacing:0.5px">历史对话</div>
      <div class="conv-list">
        <div v-if="store.loading" class="empty-conv">
          <p class="loading">加载中...</p>
        </div>
        <div v-else-if="store.conversations.length === 0" class="empty-conv">
          <div class="empty-icon">🎋</div>
          <h3 class="empty-title">暂无对话</h3>
          <p class="empty-desc">新建一个对话开始提问</p>
        </div>
        <div
          v-for="conv in store.conversations"
          :key="conv.id"
          :class="['conv-item', { active: conv.id === store.currentConvId }]"
          @click="switchConversation(conv.id)"
        >
          <span class="read-dot"></span>
          <div class="conv-title">{{ conv.title || '新对话' }}</div>
          <button class="btn-del-conv" @click.stop="deleteConv(conv.id)" title="删除对话">&times;</button>
        </div>
      </div>
      <div class="sidebar-footer">
        <a class="btn-ghost-sm" @click="router.push('/kb')">知识库管理</a>
      </div>
    </aside>

    <!-- 主聊天区 -->
    <main class="chat-main">
      <div v-if="activeConv" class="chat-titlebar">
        <span class="conv-title-text">{{ activeConv.title || '对话' }}</span>
      </div>

      <div class="messages" id="messages">
        <div v-if="localMessages.length === 0 && !store.currentConvId" class="welcome">
          <h2>开始提问</h2>
          <p>上传文档后，在这里提问即可获得基于文档内容的回答</p>
        </div>

        <template v-for="(msg, index) in localMessages" :key="msg.id || index">
          <div class="msg-pair">
            <MessageBubble :content="msg.content" :role="msg.role" :thinking="!!msg.thinking">
              <template v-if="msg.role === 1 && hasRefs(msg)">
                <button class="ref-toggle" @click="toggleRefs">
                  📄 {{ parseRefs(msg).length }} 个来源 <span class="ref-arrow">▼</span>
                </button>
                <div class="ref-cards">
                  <div v-for="ref in parseRefs(msg)" :key="ref.index || ref.snippet" class="ref-card">
                    <div class="ref-card-header">
                      <span class="ref-card-file">📄 {{ extractFilename(ref.snippet) || '文档片段' }}</span>
                      <span class="ref-card-score">{{ ref.score ? (ref.score * 100).toFixed(0) : '?' }}%</span>
                    </div>
                    <blockquote>{{ (ref.snippet || '').replace(/^【.+?】\s*/, '').substring(0, 150) }}</blockquote>
                  </div>
                </div>
              </template>
            </MessageBubble>
          </div>
        </template>

      </div>

      <!-- 输入区 -->
      <div class="input-area">
        <div class="input-row">
          <textarea
            v-model="input"
            rows="1"
            placeholder="输入你的问题..."
            :disabled="sending"
            @keydown="(e: any) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleAsk() } }"
            @input="autoGrow($event.target as HTMLTextAreaElement)"
          ></textarea>
          <button class="btn-send" @click="handleAsk" :disabled="sending || !input.trim()">{{ sending ? '...' : '发送' }}</button>
        </div>
      </div>
    </main>
  </div>

  <!-- 引用弹窗 -->
  <Teleport to="body">
    <div v-if="refModalVisible" class="modal-overlay" @click.self="refModalVisible = false">
      <div class="modal-content modal-wide">
        <div class="modal-header">
          <h3>引用来源</h3>
          <button class="modal-close" @click="refModalVisible = false">&times;</button>
        </div>
        <div class="ref-content" v-if="refDetail">
          <div class="ref-detail">
            <div class="ref-source-label"><strong>📄 {{ refDetail.title }}</strong></div>
            <p><strong>相关度:</strong> {{ refDetail.score }}%</p>
            <div class="ref-score-bar"><div class="ref-score-fill" :style="{ width: refDetail.score + '%' }"></div></div>
            <p><strong>原文片段:</strong></p>
            <blockquote>{{ refDetail.snippet }}</blockquote>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.chat-layout {
  display: flex;
  height: calc(100vh - 60px);
  overflow: hidden;
  position: relative;
}

/* 侧边栏 */
.chat-sidebar {
  width: 280px;
  background: rgba(255,252,247,0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-right: 1px solid var(--border-lt);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid var(--border-lt);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.conv-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.conv-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: var(--radius);
  cursor: pointer;
  transition: background 0.15s;
  margin-bottom: 4px;
  gap: 8px;
}
.conv-item:hover { background: var(--cinnabar-dim); }
.conv-item.active { background: rgba(28,26,24,0.04); border-left: 2px solid var(--cinnabar); }
.conv-title {
  font-size: 13px;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.read-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--cinnabar); flex-shrink: 0; }
.btn-del-conv {
  background: none; border: none;
  color: var(--border); font-size: 16px; cursor: pointer;
  padding: 0 4px; line-height: 1; flex-shrink: 0; display: none;
}
.conv-item:hover .btn-del-conv { display: block; }
.btn-del-conv:hover { color: var(--cinnabar); }
.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border-lt);
  display: flex; flex-direction: column; gap: 8px;
}
.btn-ghost-sm {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px; font-size: 12px; color: var(--ink-muted);
  background: transparent; border: 1px solid var(--border-lt);
  border-radius: var(--radius); cursor: pointer; transition: all 0.15s;
  text-decoration: none; font-weight: 500;
}
.btn-ghost-sm:hover { background: var(--cinnabar-dim); border-color: var(--cinnabar); color: var(--cinnabar); }
.empty-conv { text-align: center; padding: 40px 20px; color: var(--ink-pale); }
.empty-icon { font-size: 36px; margin-bottom: 8px; opacity: 0.5; }
.empty-title { font-family: var(--serif); font-size: 16px; color: var(--ink-muted); margin-bottom: 4px; }
.empty-desc { font-size: 13px; line-height: 1.6; }

/* 主聊天区 */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--paper);
  min-width: 0;
  position: relative;
}
.chat-titlebar {
  display: flex; align-items: center;
  padding: 12px 32px;
  border-bottom: 1px solid var(--border-lt);
  background: rgba(255,252,247,0.6);
  backdrop-filter: blur(8px);
  font-size: 14px;
  color: var(--ink);
  flex-shrink: 0;
}
.conv-title-text {
  font-family: var(--serif);
  font-weight: 600;
  letter-spacing: 0.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 32px;
  display: flex;
  flex-direction: column;
  gap: 0;
  scroll-behavior: smooth;
  position: relative;
  z-index: 1;
}

/* 消息 */
.msg-pair { position: relative; animation: msgSlideIn 0.3s ease-out; }
@keyframes msgSlideIn {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}
.msg-pair + .msg-pair {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border-lt);
}

.msg { display: flex; gap: 12px; max-width: 840px; }
.msg-user { margin-left: auto; flex-direction: row-reverse; }

.msg-avatar {
  width: 36px; height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center; justify-content: center;
  flex-shrink: 0;
}
.msg-user .msg-avatar {
  background: var(--ink);
  color: var(--paper-card);
}
.msg-ai .msg-avatar {
  background: var(--cinnabar);
  color: white;
  box-shadow: 0 2px 6px var(--cinnabar-mid);
}

.msg-content { max-width: 640px; }
.msg-user .msg-content { max-width: 660px; }

.msg-text {
  padding: 14px 18px;
  border-radius: var(--radius-lg);
  font-size: 14px;
  line-height: 1.75;
  position: relative;
}
.msg-user .msg-text {
  background: var(--ink);
  color: var(--paper-card);
  border-bottom-right-radius: 4px;
  font-weight: 500;
}
.msg-ai .msg-text {
  background: rgba(255,252,247,0.85);
  backdrop-filter: blur(8px);
  color: var(--ink);
  border-bottom-left-radius: 4px;
  box-shadow: var(--shadow);
  border: 1px solid var(--border-lt);
}

/* 来源 */
.ref-toggle {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 3px 10px; margin-top: 8px;
  background: var(--cinnabar-dim);
  border: 1px solid rgba(194,59,34,0.15);
  border-radius: 999px;
  font-size: 11px; font-weight: 500;
  color: var(--cinnabar);
  cursor: pointer; transition: all 0.15s;
  float: right;
}
.ref-toggle:hover { background: var(--cinnabar-mid); }
.ref-toggle .ref-arrow { font-size: 8px; transition: transform 0.2s; display: inline-block; }
.ref-toggle.open .ref-arrow { transform: rotate(180deg); }
.ref-cards { display: none; clear: both; padding-top: 6px; }
.msg-content.expanded-refs .ref-cards { display: block; }
.ref-card {
  background: rgba(250,246,239,0.9);
  border: 1px solid var(--border-lt);
  border-radius: var(--radius);
  padding: 10px 12px;
  margin-bottom: 4px;
  font-size: 12px;
}
.ref-card-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px; }
.ref-card-file { font-size: 12px; font-weight: 600; color: var(--ink); }
.ref-card-score { font-size: 10px; color: var(--ink-pale); background: var(--paper-dark); padding: 1px 6px; border-radius: 999px; }
.ref-card blockquote {
  margin: 2px 0 4px; padding: 4px 8px;
  background: var(--paper); border-left: 2px solid var(--cinnabar);
  border-radius: var(--radius-sm);
  font-size: 11px; color: var(--ink-muted);
  line-height: 1.4; max-height: 40px; overflow: hidden;
}

/* 思考动画 */
.thinking {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-muted);
  font-size: 13px;
  animation: thinkingPulse 1.6s ease-in-out infinite;
}
.thinking-dots {
  display: inline-block; overflow: hidden; vertical-align: bottom;
  width: 0; animation: dotFade 1.4s ease-in-out infinite;
}
.thinking-dots::after { content: '...'; letter-spacing: 1px; }
@keyframes dotFade { 0%,100% { width: 0; } 50% { width: 1.4em; } }
@keyframes thinkingPulse { 0%,100% { opacity: 0.6; } 50% { opacity: 1; } }
#thinkingPair .msg-avatar { animation: avatarPulse 1.2s ease-in-out infinite; }
@keyframes avatarPulse { 0%,100% { transform: scale(1); box-shadow: 0 2px 6px var(--cinnabar-mid); } 50% { transform: scale(1.08); box-shadow: 0 4px 14px var(--cinnabar); } }

/* 欢迎 */
.welcome {
  text-align: center;
  padding: 100px 20px;
}
.welcome h2 {
  font-family: var(--serif);
  font-size: 24px;
  color: var(--ink);
  margin-bottom: 8px;
  letter-spacing: 2px;
}
.welcome p { color: var(--ink-muted); font-size: 14px; }

/* 输入区 */
.input-area {
  padding: 12px 32px 16px;
  background: rgba(255,252,247,0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1px solid var(--border-lt);
  display: flex;
  justify-content: center;
  position: relative;
  z-index: 1;
}
.input-area .input-row {
  display: flex; align-items: stretch; gap: 0;
  max-width: min(65vw, 720px);
  width: 100%;
}
.input-area textarea {
  flex: 1; padding: 12px 16px;
  resize: none; overflow-y: auto;
  min-height: 48px; max-height: 140px;
  line-height: 1.5; font-size: 14px;
  border: 1px solid var(--border);
  border-right: none;
  border-radius: var(--radius) 0 0 var(--radius);
  background: rgba(250,246,239,0.6);
  transition: all 0.2s; font-family: var(--sans);
  outline: none;
}
.input-area textarea::placeholder { color: var(--ink-pale); }
.input-area textarea:focus {
  border-color: var(--cinnabar);
  box-shadow: 0 0 0 3px var(--cinnabar-dim);
  background: rgba(250,246,239,0.85);
}
.btn-send {
  height: 48px;
  padding: 0 22px;
  background: var(--ink);
  color: var(--paper-card);
  border: none;
  border-radius: 0 var(--radius) var(--radius) 0;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  font-family: var(--sans);
  letter-spacing: 0.5px;
  flex-shrink: 0;
}
.btn-send:hover { background: var(--ink-light); }
.btn-send:disabled { opacity: 0.35; cursor: not-allowed; }

/* 引用弹窗 */
.ref-content { font-size: 14px; }
.ref-detail .ref-source-label {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 12px; background: var(--paper-warm);
  border-radius: var(--radius); margin-bottom: 12px;
  font-size: 13px; color: var(--ink-muted);
}
.ref-detail blockquote {
  background: var(--paper); padding: 14px 16px;
  border-left: 3px solid var(--cinnabar);
  border-radius: var(--radius); color: var(--ink-muted);
  font-size: 13px; line-height: 1.6; margin: 0;
}
.ref-score-bar { height: 4px; background: var(--border-lt); border-radius: 2px; margin: 8px 0; overflow: hidden; }
.ref-score-fill { height: 100%; background: linear-gradient(90deg, var(--cinnabar), var(--gold)); border-radius: 2px; transition: width 0.3s; }
</style>
