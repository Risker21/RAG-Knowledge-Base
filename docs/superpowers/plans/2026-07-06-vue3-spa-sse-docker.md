# Vue 3 SPA + SSE 流式输出 + Docker 部署实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将前端从 Thymeleaf 升级为 Vue 3 + TypeScript SPA，后端增加 SSE 流式输出，并通过 Docker 一键部署。

**Architecture:**
- 前端：Vue 3 + TypeScript + Vite，独立 `frontend/` 目录，通过 REST API + SSE 与后端通信
- 后端：Spring Boot 3 保持不变，ChatController 增加 SSE 端点，LlmService 增加流式调用
- 部署：Docker Compose 编排 Spring Boot + Vue(Nginx) + MySQL + Redis 四个容器

**Tech Stack:** Vue 3, TypeScript, Vite, Axios, Pinia, Vue Router, marked.js + highlight.js, SSE (text/event-stream), Nginx, Docker, docker-compose

---

## File Structure

```
rag-kb/
├── frontend/                          # 新建：Vue 3 SPA
│   ├── Dockerfile                     # Nginx 容器化部署
│   ├── nginx.conf                     # SPA 路由转发配置
│   ├── package.json
│   ├── tsconfig.json
│   ├── vite.config.ts                 # 代理 /api 到后端
│   ├── index.html
│   └── src/
│       ├── main.ts                    # 入口
│       ├── App.vue                    # 根组件 + Router View
│       ├── api/
│       │   ├── client.ts              # Axios 实例（拦截器）
│       │   ├── auth.ts                # POST /api/auth/login, register, logout
│       │   ├── captcha.ts             # GET /api/captcha/image
│       │   ├── kb.ts                  # CRUD /kb/api/*
│       │   ├── doc.ts                 # CRUD /doc/api/*
│       │   └── chat.ts               # SSE /chat/api/ask/stream, REST /chat/api/*
│       ├── types/
│       │   └── index.ts              # User, Kb, Document, Message, ChatRequest 等接口
│       ├── router/
│       │   └── index.ts              # 路由：/, /login, /register, /kb, /kb/:id, /chat/:kbId
│       ├── stores/
│       │   ├── user.ts               # 用户状态
│       │   └── chat.ts               # 对话/消息状态
│       ├── views/
│       │   ├── LoginView.vue
│       │   ├── RegisterView.vue
│       │   ├── KbListView.vue        # 知识库列表
│       │   ├── KbDetailView.vue      # 文档管理
│       │   └── ChatView.vue          # 聊天页（SSE 流式）
│       ├── components/
│       │   ├── AppHeader.vue         # 顶部导航
│       │   ├── KbCard.vue            # 知识库卡片
│       │   ├── DocList.vue           # 文档列表
│       │   ├── MessageBubble.vue     # 消息气泡（Markdown 渲染）
│       │   └── ReferencesModal.vue   # 引用弹窗
│       └── styles/
│           └── main.css              # 全局样式（墨韵主题）
├── docker-compose.yml                # 新建：服务编排
├── Dockerfile                        # 新建：Spring Boot 多阶段构建
├── src/main/java/com/rag/kb/
│   ├── controller/
│   │   └── ChatController.java       # 修改：增加 SSE 端点
│   └── service/
│       ├── LlmService.java           # 修改：增加流式 API 调用
│       └── SseService.java           # 新建：SSE 发射器管理
└── src/main/resources/
    ├── application.yml                # 修改：添加 CORS 配置
    └── static/                        # 删除或保留（前端不再使用）
```

---

### Task 1: 后端 SSE 流式支持

**Files:**
- Create: `src/main/java/com/rag/kb/service/SseService.java`
- Modify: `src/main/java/com/rag/kb/service/LlmService.java`
- Modify: `src/main/java/com/rag/kb/controller/ChatController.java`

- [ ] **Step 1: 创建 SseService.java**

```java
package com.rag.kb.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String conversationId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(conversationId, emitter);
        emitter.onCompletion(() -> emitters.remove(conversationId));
        emitter.onTimeout(() -> emitters.remove(conversationId));
        return emitter;
    }

    public void send(String conversationId, String data) {
        SseEmitter emitter = emitters.get(conversationId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().data(data));
            } catch (Exception e) {
                emitters.remove(conversationId);
            }
        }
    }

    public void complete(String conversationId) {
        SseEmitter emitter = emitters.remove(conversationId);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }

    public void error(String conversationId, String error) {
        SseEmitter emitter = emitters.remove(conversationId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("error").data(error));
                emitter.complete();
            } catch (Exception ignored) {}
        }
    }
}
```

- [ ] **Step 2: LlmService 增加流式调用方法**

```java
// 在 LlmService 中新增方法
public String chatStream(String systemPrompt, String userMessage, Consumer<String> onToken) {
    // 使用 RestTemplate 或 WebClient 流式读取 SSE 响应
    // 火山引擎 Ark API 兼容 OpenAI 的 streaming 格式
    // 逐 token 回调 onToken，最后返回完整文本
}
```

实现细节：使用 Spring `WebClient` 或 `RestTemplate` 的 `ResponseExtractor` 逐行读取 `data: {...}` 事件流，解析 `choices[0].delta.content`，每拿到一段 token 调用 `onToken.accept(token)`。

- [ ] **Step 3: ChatController 增加 SSE 端点**

```java
// 新增端点
@GetMapping("/chat/api/ask/stream")
public SseEmitter askStream(@SessionAttribute("userId") Long userId,
                            @RequestParam Long conversationId,
                            @RequestParam Long kbId,
                            @RequestParam String question) {
    String convId = conversationId.toString();
    SseEmitter emitter = sseService.createEmitter(convId);
    
    // 异步执行 RAG 流程
    CompletableFuture.runAsync(() -> {
        try {
            ChatResponse response = chatService.processQuestion(userId, kbId, conversationId, question, 
                token -> sseService.send(convId, token));
            sseService.send(convId, "[DONE]" + JSON.toJSONString(response.getReferences()));
            sseService.complete(convId);
        } catch (Exception e) {
            sseService.error(convId, e.getMessage());
        }
    });
    
    return emitter;
}
```

- [ ] **Step 4: ChatService 增加流式回调参数**

修改 `ChatService.processQuestion()` 方法签名，增加 `Consumer<String> onToken` 参数，传入 `LlmService.chatStream()`。

- [ ] **Step 5: 验证编译通过**

```bash
mvn compile -q
```

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/rag/kb/service/SseService.java src/main/java/com/rag/kb/service/LlmService.java src/main/java/com/rag/kb/controller/ChatController.java -n
```

---

### Task 2: 前端 Vue 3 + TypeScript 项目初始化

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/index.html`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/env.d.ts`

- [ ] **Step 1: 创建 package.json**

```json
{
  "name": "rag-kb-frontend",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "vue": "^3.4.0",
    "vue-router": "^4.3.0",
    "pinia": "^2.1.0",
    "axios": "^1.7.0",
    "marked": "^12.0.0",
    "highlight.js": "^11.9.0"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.0.0",
    "typescript": "^5.4.0",
    "vite": "^5.2.0",
    "vue-tsc": "^2.0.0"
  }
}
```

- [ ] **Step 2: 创建 tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "preserve",
    "strict": true,
    "noUnusedLocals": false,
    "noUnusedParameters": false,
    "noFallthroughCasesInSwitch": true,
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue", "env.d.ts"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

- [ ] **Step 3: 创建 vite.config.ts**

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

- [ ] **Step 4: 创建 index.html**

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>墨韵 - RAG 知识库</title>
</head>
<body>
  <div id="app"></div>
  <script type="module" src="/src/main.ts"></script>
</body>
</html>
```

- [ ] **Step 5: 创建 main.ts**

```typescript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './styles/main.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
```

- [ ] **Step 6: 验证前端可构建**

```bash
cd frontend
npm install
npm run build
```

---

### Task 3: 前端基础架构 - API 层 + 类型 + 路由

**Files:**
- Create: `frontend/src/types/index.ts`
- Create: `frontend/src/api/client.ts`
- Create: `frontend/src/api/auth.ts`
- Create: `frontend/src/api/captcha.ts`
- Create: `frontend/src/api/kb.ts`
- Create: `frontend/src/api/doc.ts`
- Create: `frontend/src/api/chat.ts`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/stores/user.ts`

- [ ] **Step 1: 类型定义 types/index.ts**

```typescript
export interface User {
  id: number
  username: string
  createdAt: string
}

export interface KnowledgeBase {
  id: number
  userId: number
  name: string
  description: string
  createdAt: string
}

export interface Document {
  id: number
  kbId: number
  userId: number
  filename: string
  fileType: string
  fileSize: number
  status: number
  chunkCount: number
  errorMsg: string
  createdAt: string
}

export interface Conversation {
  id: number
  userId: number
  kbId: number
  title: string
  createdAt: string
}

export interface Message {
  id: number
  conversationId: number
  role: number
  content: string
  referencesJson: string
  createdAt: string
}

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface ChatRequest {
  conversationId: number
  kbId: number
  question: string
}

export interface ChatResponse {
  answer: string
  references: Array<{ title: string; content: string }>
  conversationId: number
}

export interface Reference {
  title: string
  content: string
}
```

- [ ] **Step 2: Axios 实例 api/client.ts**

```typescript
import axios from 'axios'

const client = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

client.interceptors.response.use(
  res => res,
  error => {
    if (error.response?.status === 401) {
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default client
```

- [ ] **Step 3: 各 API 模块**

api/auth.ts:
```typescript
import client from './client'
import type { ApiResult, User } from '@/types'

export const login = (username: string, password: string) =>
  client.post<ApiResult<User>>('/auth/login', { username, password })

export const register = (username: string, password: string, confirmPassword: string) =>
  client.post<ApiResult<User>>('/auth/register', { username, password, confirmPassword })

export const logout = () =>
  client.get('/auth/logout')
```

api/captcha.ts:
```typescript
const CAPTCHA_URL = '/api/captcha/image'

export const getCaptchaUrl = () => `${CAPTCHA_URL}?t=${Date.now()}`
```

api/kb.ts:
```typescript
import client from './client'
import type { ApiResult, KnowledgeBase } from '@/types'

export const listKb = () =>
  client.get<ApiResult<KnowledgeBase[]>>('/kb/api/list')

export const createKb = (name: string, description: string) =>
  client.post<ApiResult<KnowledgeBase>>('/kb/api/create', { name, description })

export const deleteKb = (id: number) =>
  client.delete(`/kb/api/${id}`)
```

api/doc.ts:
```typescript
import client from './client'
import type { ApiResult, Document } from '@/types'

export const listDocs = (kbId: number) =>
  client.get<ApiResult<Document[]>>(`/doc/api/list/${kbId}`)

export const uploadDoc = (kbId: number, file: File) => {
  const form = new FormData()
  form.append('file', file)
  form.append('kbId', kbId.toString())
  return client.post<ApiResult<any>>('/doc/api/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const deleteDoc = (id: number) =>
  client.delete(`/doc/api/${id}`)
```

api/chat.ts:
```typescript
import client from './client'
import type { ApiResult, Conversation, Message, ChatRequest, ChatResponse } from '@/types'

export const listConversations = (kbId: number) =>
  client.get<ApiResult<Conversation[]>>(`/chat/api/conversations/${kbId}`)

export const startConversation = (kbId: number, title?: string) =>
  client.post<ApiResult<Conversation>>('/chat/api/start', { kbId, title })

export const deleteConversation = (id: number) =>
  client.delete(`/chat/api/conversations/${id}`)

export const getMessages = (convId: number) =>
  client.get<ApiResult<Message[]>>(`/chat/api/messages/${convId}`)

export const ask = (data: ChatRequest) =>
  client.post<ApiResult<ChatResponse>>('/chat/api/ask', data)
```

- [ ] **Step 4: 路由 router/index.ts**

```typescript
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/kb' },
    { path: '/login', component: () => import('@/views/LoginView.vue') },
    { path: '/register', component: () => import('@/views/RegisterView.vue') },
    { path: '/kb', component: () => import('@/views/KbListView.vue'), meta: { requiresAuth: true } },
    { path: '/kb/:id', component: () => import('@/views/KbDetailView.vue'), meta: { requiresAuth: true } },
    { path: '/chat/:kbId', component: () => import('@/views/ChatView.vue'), meta: { requiresAuth: true } },
  ]
})

router.beforeEach((to, from, next) => {
  const token = sessionStorage.getItem('userId')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
```

- [ ] **Step 5: 用户状态 stores/user.ts**

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { User } from '@/types'
import * as authApi from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null)
  const userId = ref<number | null>(Number(sessionStorage.getItem('userId')) || null)
  const username = ref<string>(sessionStorage.getItem('username') || '')

  async function doLogin(name: string, password: string) {
    const res = await authApi.login(name, password)
    if (res.data.code === 0) {
      user.value = res.data.data
      userId.value = res.data.data.id
      username.value = res.data.data.username
      sessionStorage.setItem('userId', String(res.data.data.id))
      sessionStorage.setItem('username', res.data.data.username)
    }
    return res.data
  }

  async function doRegister(name: string, password: string, confirm: string) {
    const res = await authApi.register(name, password, confirm)
    return res.data
  }

  async function doLogout() {
    await authApi.logout()
    user.value = null
    userId.value = null
    username.value = ''
    sessionStorage.clear()
  }

  return { user, userId, username, doLogin, doRegister, doLogout }
})
```

---

### Task 4: 前端全局组件

**Files:**
- Create: `frontend/src/App.vue`
- Create: `frontend/src/components/AppHeader.vue`
- Create: `frontend/src/styles/main.css`

- [ ] **Step 1: App.vue**

```vue
<script setup lang="ts">
import AppHeader from '@/components/AppHeader.vue'
</script>

<template>
  <AppHeader />
  <main class="main-container">
    <RouterView />
  </main>
</template>
```

- [ ] **Step 2: AppHeader.vue**

```vue
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const handleLogout = async () => {
  await userStore.doLogout()
  router.push('/login')
}
</script>

<template>
  <header class="app-header">
    <div class="header-inner">
      <RouterLink to="/kb" class="logo">墨韵</RouterLink>
      <nav class="nav-links">
        <RouterLink to="/kb">知识库</RouterLink>
      </nav>
      <div class="user-area">
        <span v-if="userStore.username" class="username">{{ userStore.username }}</span>
        <button v-if="userStore.userId" @click="handleLogout" class="btn-logout">退出</button>
      </div>
    </div>
  </header>
</template>
```

- [ ] **Step 3: main.css（墨韵主题）**

```css
:root {
  --primary: #1a3a3a;
  --primary-light: #2d5a5a;
  --accent: #c9a96e;
  --bg: #f5f0eb;
  --bg-card: #ffffff;
  --text: #2c2c2c;
  --text-light: #666;
  --border: #e0d8d0;
  --shadow: 0 2px 12px rgba(0,0,0,0.08);
  --radius: 8px;
}

* { margin: 0; padding: 0; box-sizing: border-box; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Noto Sans SC", sans-serif;
  background: var(--bg);
  color: var(--text);
  min-height: 100vh;
}
a { color: var(--primary-light); text-decoration: none; }
a:hover { color: var(--accent); }

.app-header {
  background: var(--primary);
  color: #fff;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 56px;
  display: flex;
  align-items: center;
  gap: 32px;
}
.logo {
  font-size: 20px;
  font-weight: 700;
  color: var(--accent) !important;
  letter-spacing: 2px;
}
.nav-links { display: flex; gap: 16px; }
.nav-links a { color: rgba(255,255,255,0.85); font-size: 14px; }
.nav-links a:hover { color: var(--accent); }
.user-area { margin-left: auto; display: flex; align-items: center; gap: 12px; }
.username { font-size: 14px; opacity: 0.85; }
.btn-logout {
  background: transparent;
  border: 1px solid rgba(255,255,255,0.3);
  color: #fff;
  padding: 4px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.btn-logout:hover { border-color: var(--accent); color: var(--accent); }
.main-container { max-width: 1200px; margin: 0 auto; padding: 24px; }
```

---

### Task 5: 前端页面 - 登录 & 注册

**Files:**
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/RegisterView.vue`

- [ ] **Step 1: LoginView.vue**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getCaptchaUrl } from '@/api/captcha'

const router = useRouter()
const userStore = useUserStore()
const username = ref('')
const password = ref('')
const captcha = ref('')
const captchaUrl = ref(getCaptchaUrl())
const error = ref('')

const refreshCaptcha = () => { captchaUrl.value = getCaptchaUrl() }

const handleLogin = async () => {
  try {
    const res = await userStore.doLogin(username.value, password.value)
    if (res.code === 0) {
      router.push('/kb')
    } else {
      error.value = res.message || '登录失败'
      refreshCaptcha()
    }
  } catch {
    error.value = '网络错误'
    refreshCaptcha()
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="auth-title">墨韵</h1>
      <p class="auth-subtitle">RAG 知识库问答系统</p>
      <form @submit.prevent="handleLogin" class="auth-form">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="username" type="text" required placeholder="请输入用户名" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="password" type="password" required placeholder="请输入密码" />
        </div>
        <div class="form-group">
          <label>验证码</label>
          <div class="captcha-row">
            <input v-model="captcha" type="text" required placeholder="验证码" maxlength="4" />
            <img :src="captchaUrl" @click="refreshCaptcha" class="captcha-img" alt="验证码" />
          </div>
        </div>
        <p v-if="error" class="error-msg">{{ error }}</p>
        <button type="submit" class="btn-primary">登 录</button>
      </form>
      <p class="auth-link">没有账号？<RouterLink to="/register">立即注册</RouterLink></p>
    </div>
  </div>
</template>
```

- [ ] **Step 2: RegisterView.vue**（类似结构，注册表单 + 调用 userStore.doRegister）

---

### Task 6: 前端页面 - 知识库

**Files:**
- Create: `frontend/src/views/KbListView.vue`
- Create: `frontend/src/views/KbDetailView.vue`
- Create: `frontend/src/components/KbCard.vue`

- [ ] **Step 1: KbListView.vue**

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import * as kbApi from '@/api/kb'
import type { KnowledgeBase } from '@/types'
import KbCard from '@/components/KbCard.vue'

const router = useRouter()
const list = ref<KnowledgeBase[]>([])
const showModal = ref(false)
const newName = ref('')
const newDesc = ref('')

const fetchList = async () => {
  const res = await kbApi.listKb()
  if (res.data.code === 0) list.value = res.data.data
}

const createKb = async () => {
  await kbApi.createKb(newName.value, newDesc.value)
  showModal.value = false
  newName.value = ''
  newDesc.value = ''
  fetchList()
}

const deleteKb = async (id: number) => {
  if (!confirm('确定删除？')) return
  await kbApi.deleteKb(id)
  fetchList()
}

onMounted(fetchList)
</script>

<template>
  <div class="kb-page">
    <div class="page-header">
      <h2>我的知识库</h2>
      <button @click="showModal = true" class="btn-primary">+ 新建</button>
    </div>
    <div class="kb-grid">
      <KbCard v-for="kb in list" :key="kb.id" :kb="kb" @enter="router.push(`/kb/${kb.id}`)" @delete="deleteKb(kb.id)" />
    </div>
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal">
        <h3>新建知识库</h3>
        <input v-model="newName" placeholder="名称" />
        <textarea v-model="newDesc" placeholder="描述（可选）" rows="3"></textarea>
        <button @click="createKb" class="btn-primary">创建</button>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 2: KbCard.vue**

```vue
<script setup lang="ts">
import type { KnowledgeBase } from '@/types'
defineProps<{ kb: KnowledgeBase }>()
defineEmits<{ enter: []; delete: [] }>()
</script>

<template>
  <div class="kb-card" @click="$emit('enter')">
    <h3>{{ kb.name }}</h3>
    <p v-if="kb.description">{{ kb.description }}</p>
    <div class="kb-card-footer">
      <span class="kb-date">{{ kb.createdAt?.slice(0, 10) }}</span>
      <button @click.stop="$emit('delete')" class="btn-danger-sm">删除</button>
    </div>
  </div>
</template>
```

- [ ] **Step 3: KbDetailView.vue**（文档列表 + 上传功能）

---

### Task 7: 前端页面 - 聊天（核心）

**Files:**
- Create: `frontend/src/views/ChatView.vue`
- Create: `frontend/src/components/MessageBubble.vue`
- Create: `frontend/src/components/ReferencesModal.vue`
- Create: `frontend/src/stores/chat.ts`

- [ ] **Step 1: ChatView.vue**

核心功能：
- 左侧对话列表（可切换/新建/删除）
- 右侧消息列表 + 输入框
- SSE 流式接收 AI 回答（打字机效果）
- 引用来源弹窗

```vue
<script setup lang="ts">
// SSE 流式消费：使用 fetch + ReadableStream 或 EventSource
// 由于需要 POST 请求，使用 fetch 手动读取 stream

const handleAsk = async () => {
  if (!input.value.trim() || !currentConvId.value) return
  
  messages.value.push({ role: 0, content: input.value, id: 0 } as any)
  const question = input.value
  input.value = ''
  
  // 添加占位 AI 消息
  const aiMsg: any = { role: 1, content: '', references: null }
  messages.value.push(aiMsg)
  
  const params = new URLSearchParams({
    conversationId: String(currentConvId.value),
    kbId: String(props.kbId),
    question
  })
  
  const res = await fetch(`/api/chat/api/ask/stream?${params}`, {
    headers: { 'Content-Type': 'application/json' }
  })
  
  const reader = res.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // 解析 SSE data: 行
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''
    for (const line of lines) {
      if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (data === '[DONE]') break
        try {
          const parsed = JSON.parse(data)
          if (parsed.token) {
            aiMsg.content += parsed.token
          }
        } catch {}
      }
    }
  }
}
</script>
```

- [ ] **Step 2: MessageBubble.vue**

使用 `marked` 渲染 Markdown，`highlight.js` 代码高亮。

```vue
<script setup lang="ts">
import { ref, watch } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'

const props = defineProps<{ content: string; role: number }>()
const html = ref('')

marked.setOptions({
  highlight: (code: string, lang: string) => {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  }
})

watch(() => props.content, (val) => {
  html.value = marked(val || '')
}, { immediate: true })
</script>

<template>
  <div :class="['message', role === 0 ? 'user' : 'ai']">
    <div class="avatar">{{ role === 0 ? 'U' : '墨' }}</div>
    <div class="bubble" v-html="html"></div>
  </div>
</template>
```

---

### Task 8: 后端 CORS 配置

**Files:**
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: 添加 CORS 配置**

在 `application.yml` 中新增：
```yaml
spring:
  web:
    cors:
      allowed-origins: "http://localhost:3000"
      allowed-methods: GET,POST,PUT,DELETE,OPTIONS
      allowed-headers: "*"
      allow-credentials: true
```

或在代码中配置（推荐）：

```java
// 新建 config/CorsConfig.java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowCredentials(true);
    }
}
```

---

### Task 9: Docker 容器化

**Files:**
- Create: `Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`
- Create: `docker-compose.yml`

- [ ] **Step 1: Spring Boot Dockerfile（项目根目录）**

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/rag-kb-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: 前端 Dockerfile**

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

- [ ] **Step 3: nginx.conf**

```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location /api {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_buffering off;
        proxy_cache off;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- [ ] **Step 4: docker-compose.yml**

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: Wordl9543
      MYSQL_DATABASE: rag_kb
    ports:
      - "3307:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      - ./src/main/resources/schema.sql:/docker-entrypoint-initdb.d/schema.sql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6380:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/rag_kb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
      SPRING_DATASOURCE_PASSWORD: Wordl9543
      SPRING_DATA_REDIS_HOST: redis
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy

  frontend:
    build: frontend
    ports:
      - "80:80"
    depends_on:
      - backend

volumes:
  mysql-data:
```

---

### Task 10: .dockerignore

**Files:**
- Create: `.dockerignore`

- [ ] **Step 1: .dockerignore**

```
target/
.idea/
*.iml
.git
node_modules/
**/node_modules/
frontend/src/
*.log
uploads/
```

---

### Task 11: 集成测试与验证

- [ ] **Step 1: 本地构建前端验证**

```bash
cd frontend && npm install && npm run build
```

- [ ] **Step 2: Docker Compose 启动验证**

```bash
docker-compose up --build
```

- [ ] **Step 3: 访问验证**

浏览器打开 `http://localhost`，验证注册 → 登录 → 创建知识库 → 上传文档 → 提问流程。
