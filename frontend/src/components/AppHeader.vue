<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const show = () => userStore.userId && !route.meta.noHeader

const handleLogout = async () => {
  await userStore.doLogout()
  router.replace('/login')
}
</script>

<template>
  <header v-if="show()" class="app-header">
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
}
.app-header h1 {
  font-family: var(--serif);
  font-size: 18px;
  letter-spacing: 2px;
  color: var(--ink);
  font-weight: 700;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: var(--ink-muted);
}
.btn-logout {
  color: var(--ink-muted);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.15s;
}
.btn-logout:hover { color: var(--cinnabar); }
</style>
