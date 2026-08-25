<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const show = () => userStore.userId && !route.meta.noHeader
// 聊天页在移动端自带 .mobile-topbar 顶栏，全局顶栏在聊天页移动端隐藏，避免重复占用高度导致底部输入框被挤出屏幕
const isChatRoute = () => route.path.startsWith('/chat')

const handleLogout = async () => {
  await userStore.doLogout()
  router.replace('/login')
}
</script>

<template>
  <header v-if="show()" class="app-header" :class="{ 'hide-on-mobile': isChatRoute() }">
    <div class="app-header-left">
      <span class="seal">墨</span>
      <h1>墨 韵 · 知识库</h1>
    </div>
    <div class="header-right">
      <span>{{ userStore.username }}</span>
      <a class="btn-logout" @click="handleLogout">退出</a>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  background: rgba(255,252,247,0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-lt);
  padding: 0 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: 100;
}
.app-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.app-header h1 {
  font-family: var(--serif);
  font-size: 18px;
  letter-spacing: 2px;
  color: var(--ink);
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: var(--ink-muted);
  flex-shrink: 0;
}
.btn-logout {
  color: var(--ink-muted);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.15s;
}
.btn-logout:hover { color: var(--cinnabar); }

/* 移动端响应式 */
@media (max-width: 768px) {
  .app-header { padding: 0 14px; height: 52px; }
  .app-header.hide-on-mobile { display: none; }
  .app-header h1 { font-size: 15px; letter-spacing: 1px; }
  .app-header-left { gap: 6px; }
  .header-right { gap: 8px; font-size: 13px; }
  .header-right span:not(.seal) { max-width: 70px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .btn-logout { font-size: 12px; }
}
</style>