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
const loading = ref(false)

const refreshCaptcha = () => { captchaUrl.value = getCaptchaUrl(); captcha.value = '' }

async function handleLogin() {
  error.value = ''
  loading.value = true
  try {
    const res = await userStore.doLogin(username.value, password.value, captcha.value)
    if (res.code === 200) {
      router.push('/kb')
    } else {
      error.value = res.message || '登录失败'
      refreshCaptcha()
    }
  } catch {
    error.value = '网络错误，请检查后端服务'
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="ink-particle" v-for="i in 10" :key="i"></div>

  <div class="auth-container">
    <div class="auth-card">
      <h1 class="auth-title">墨 韵</h1>
      <p class="auth-subtitle">智能文档问答 · RAG 知识库</p>

      <form @submit.prevent="handleLogin" class="auth-form">
        <div class="form-group">
          <label class="form-label">用户名</label>
          <input class="form-input" v-model="username" type="text" required placeholder="输入用户名" />
        </div>
        <div class="form-group">
          <label class="form-label">密码</label>
          <input class="form-input" v-model="password" type="password" required placeholder="输入密码" />
        </div>
        <div class="form-group">
          <label class="form-label">验证码</label>
          <div class="captcha-row">
            <input class="form-input captcha-input" v-model="captcha" type="text" required placeholder="输入验证码" maxlength="4" />
            <img :src="captchaUrl" @click="refreshCaptcha" class="captcha-img" alt="验证码" />
          </div>
        </div>
        <p v-if="error" class="error-msg">{{ error }}</p>
        <button type="submit" class="btn btn-accent btn-full" :disabled="loading">{{ loading ? '登录中...' : '登 录' }}</button>
      </form>
      <p class="auth-link">还没有账号？<RouterLink to="/register">注册账号</RouterLink></p>
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

<style>
@import url('https://fonts.googleapis.com/css2?family=Noto+Serif+SC:wght@600;700&display=swap');
</style>

<style scoped>
.ink-particle {
  position: fixed;
  width: 6px; height: 6px;
  background: rgba(28,26,24,0.06);
  border-radius: 50%;
  pointer-events: none;
  z-index: 0;
  animation: floatUp linear infinite;
}
.ink-particle:nth-child(1)  { left: 5%;  animation-duration: 18s; animation-delay: 0s;   width: 8px; height: 8px; }
.ink-particle:nth-child(2)  { left: 15%; animation-duration: 22s; animation-delay: 2s;   width: 4px; height: 4px; }
.ink-particle:nth-child(3)  { left: 25%; animation-duration: 16s; animation-delay: 5s;   width: 6px; height: 6px; }
.ink-particle:nth-child(4)  { left: 35%; animation-duration: 20s; animation-delay: 1s;   width: 5px; height: 5px; }
.ink-particle:nth-child(5)  { left: 45%; animation-duration: 24s; animation-delay: 7s;   width: 7px; height: 7px; }
.ink-particle:nth-child(6)  { left: 55%; animation-duration: 19s; animation-delay: 3s;   width: 3px; height: 3px; }
.ink-particle:nth-child(7)  { left: 65%; animation-duration: 21s; animation-delay: 6s;   width: 5px; height: 5px; }
.ink-particle:nth-child(8)  { left: 75%; animation-duration: 17s; animation-delay: 4s;   width: 6px; height: 6px; }
.ink-particle:nth-child(9)  { left: 85%; animation-duration: 23s; animation-delay: 8s;   width: 4px; height: 4px; }
.ink-particle:nth-child(10) { left: 92%; animation-duration: 20s; animation-delay: 2s;   width: 8px; height: 8px; }

@keyframes floatUp {
  0%   { transform: translateY(110vh) scale(0);  opacity: 0; }
  10%  { opacity: 0.5; }
  90%  { opacity: 0.3; }
  100% { transform: translateY(-10vh) scale(1); opacity: 0; }
}

.auth-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 20px;
  position: relative;
  z-index: 1;
}

.auth-card {
  background: var(--paper-card);
  border-radius: var(--radius-lg);
  padding: 48px 40px 36px;
  width: 380px;
  box-shadow: var(--shadow-md);
  text-align: center;
  position: relative;
}

.auth-title {
  font-family: var(--serif);
  font-size: 32px;
  font-weight: 700;
  color: var(--ink);
  letter-spacing: 8px;
  margin-bottom: 6px;
}

.auth-subtitle {
  font-size: 13px;
  color: var(--ink-muted);
  margin-bottom: 32px;
}

.auth-form { text-align: left; }

.form-group { margin-bottom: 20px; }

.form-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-light);
  margin-bottom: 6px;
  letter-spacing: 0.3px;
}

.form-input {
  width: 100%;
  padding: 12px 16px;
  background: rgba(255,252,247,0.8);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  font-size: 14px;
  font-family: var(--sans);
  color: var(--ink);
  outline: none;
  transition: border-color 0.25s, box-shadow 0.25s, background 0.25s;
}

.form-input:focus {
  border-color: var(--cinnabar);
  box-shadow: 0 0 0 3px var(--cinnabar-dim);
  background: rgba(255,252,247,0.95);
}

.form-input::placeholder { color: var(--ink-pale); }

.captcha-row { display: flex; gap: 8px; }
.captcha-input { flex: 1; }
.captcha-img { height: 42px; border-radius: 6px; cursor: pointer; border: 1px solid var(--border); }

.error-msg {
  color: var(--cinnabar);
  font-size: 13px;
  margin-bottom: 16px;
  text-align: center;
}

.btn-accent {
  background: var(--cinnabar);
  color: #fff;
  width: 100%;
  padding: 12px;
  font-size: 15px;
  letter-spacing: 2px;
}
.btn-accent:hover { background: var(--cinnabar-lt); }
.btn-accent:disabled { opacity: 0.6; cursor: not-allowed; }

.auth-link {
  margin-top: 24px;
  font-size: 13px;
  color: var(--ink-muted);
}

.mountain-scene {
  position: fixed;
  bottom: 0; left: 0; right: 0;
  height: 120px;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
  color: var(--ink);
  opacity: 0.06;
}
.mountain-scene svg {
  width: 100%;
  height: 100%;
}
</style>
