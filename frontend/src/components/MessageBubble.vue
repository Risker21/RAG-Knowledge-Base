<script setup lang="ts">
import { ref, computed } from 'vue'
import { marked } from 'marked'
import { markedHighlight } from 'marked-highlight'
import hljs from 'highlight.js'

const props = defineProps<{ content: string; role: number; thinking?: boolean }>()

marked.use(markedHighlight({
  langPrefix: 'hljs language-',
  highlight(code: string, lang: string) {
    if (code.includes('hljs-')) {
      return code
    }
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  }
}))

marked.setOptions({ breaks: true })

function decodeHtmlEntities(text: string): string {
  const textarea = document.createElement('textarea')
  textarea.innerHTML = text
  return textarea.value
}

function cleanContent(content: string): string {
  if (!content) return ''
  
  let cleaned = content.trim()
  
  cleaned = cleaned.replace(/\[DONE\]/gi, '')
  
  cleaned = cleaned.replace(/^\s*\{\s*\}\s*$/, '')
  cleaned = cleaned.replace(/^\s*\[\s*\]\s*$/, '')
  
  return cleaned
}

function detectAndFixFormat(content: string): string {
  if (!content) return ''
  
  if (content.includes('hljs-keyword') || content.includes('hljs-string') || content.includes('hljs-comment')) {
    const decoded = decodeHtmlEntities(content)
    const wrapped = decoded.replace(/<pre[^>]*>(.*?)<\/pre>/gis, '<pre><code>$1</code></pre>')
    return wrapped
  }
  
  if (content.includes('&lt;') && content.includes('&gt;') && content.includes('class=')) {
    const decoded = decodeHtmlEntities(content)
    return decoded
  }
  
  return null
}

const html = computed(() => {
  try {
    const content = cleanContent(props.content || '')
    
    if (!content) {
      return '<div class="error-message">😔 AI 服务返回内容为空，请稍后重试</div>'
    }
    
    const fixedContent = detectAndFixFormat(content)
    if (fixedContent) {
      return fixedContent
    }
    
    const decodedContent = decodeHtmlEntities(content)
    return marked(decodedContent)
  } catch (error) {
    console.error('Markdown rendering error:', error)
    return '<div class="error-message">😔 内容渲染失败，请稍后重试</div>'
  }
})

const collapsed = ref(true)
const isLong = computed(() => {
  const content = props.content || ''
  return content.length > 400 && !content.includes('error') && !content.includes('失败')
})
const isUser = computed(() => props.role === 0)

function toggleCollapse() {
  collapsed.value = !collapsed.value
}

function copyAnswer() {
  const text = props.content || ''
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text)
  }
}

const USER_ICON = '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>'
const AI_ICON = '<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M12 2l2.4 7.2L22 9l-6 5.2 2.4 7.8L12 16l-6.4 6L8 14.2 2 9l7.6-.2z"/></svg>'
</script>

<template>
    <div :class="['msg', isUser ? 'msg-user' : 'msg-ai']">
      <div class="msg-avatar" v-html="isUser ? USER_ICON : AI_ICON"></div>
      <div class="msg-content">
        <div class="msg-text">
          <button v-if="!isUser" class="btn-copy-answer" title="复制答案" @click="copyAnswer">📋</button>
          <span v-if="props.thinking" class="thinking">正在思考<span class="thinking-dots"></span></span>
          <div v-else :class="['msg-text-body', { collapsed: isLong && collapsed }]" v-html="html"></div>
          <button v-if="isLong && !props.thinking" class="msg-collapse-toggle" @click="toggleCollapse">{{ collapsed ? '展开全文 ▼' : '收起全文 ▲' }}</button>
        </div>
        <slot></slot>
      </div>
    </div>
</template>

<style scoped>
.msg { display: flex; gap: 12px; max-width: 840px; }
.msg-user { margin-left: auto; flex-direction: row-reverse; }
.msg-ai { margin-right: auto; }

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

.msg-text-body { max-height: none; overflow: visible; }
.msg-text-body.collapsed { max-height: 300px; overflow: hidden; position: relative; }
.msg-text-body.collapsed::after {
  content: ''; position: absolute;
  bottom: 0; left: 0; right: 0;
  height: 48px;
  background: linear-gradient(transparent, rgba(255,252,247,0.85));
  pointer-events: none;
}

.msg-collapse-toggle {
  display: block; width: 100%;
  padding: 6px; margin-top: 0;
  background: transparent;
  border: 1px solid var(--border-lt);
  border-top: none;
  border-radius: 0 0 var(--radius) var(--radius);
  font-size: 12px; color: var(--ink-muted);
  cursor: pointer; font-family: var(--sans);
}
.msg-collapse-toggle:hover { background: var(--cinnabar-dim); color: var(--cinnabar); }

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

.btn-copy-answer {
  position: absolute;
  top: 6px; right: 6px;
  width: 28px; height: 28px;
  display: flex; align-items: center; justify-content: center;
  background: transparent; border: none;
  color: var(--ink-pale); font-size: 13px;
  cursor: pointer; border-radius: 4px;
  opacity: 0;
  transition: all 0.15s;
}
.msg-ai .msg-text:hover .btn-copy-answer { opacity: 1; }
.btn-copy-answer:hover { background: var(--cinnabar-dim); color: var(--cinnabar); }

/* Markdown */
:deep(.msg-text-body p) { margin: 6px 0; }
:deep(.msg-text-body ul), :deep(.msg-text-body ol) { padding-left: 22px; margin: 6px 0; }
:deep(.msg-text-body li) { margin-bottom: 3px; line-height: 1.7; }
:deep(.msg-text-body h1), :deep(.msg-text-body h2), :deep(.msg-text-body h3), :deep(.msg-text-body h4) {
  font-family: var(--serif);
  margin: 14px 0 6px;
  color: var(--ink);
}
:deep(.msg-text-body strong) { color: var(--ink); }
:deep(.msg-text-body a) { color: var(--cinnabar); }
:deep(.msg-text-body code):not(pre code) {
  background: var(--cinnabar-dim);
  color: var(--cinnabar);
  padding: 2px 7px;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 500;
  font-family: var(--mono);
}
:deep(pre) { background: #1C1A18 !important; color: #cdd6f4; padding: 16px; border-radius: 8px; overflow-x: auto; margin: 12px 0; font-size: 13px; line-height: 1.5; }
:deep(code) { font-size: 13px; font-family: var(--mono); }

.error-message {
  color: var(--cinnabar);
  font-size: 14px;
  line-height: 1.6;
  padding: 8px;
  background: var(--cinnabar-dim);
  border-radius: var(--radius);
}
</style>
