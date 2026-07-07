import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import * as authApi from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const userId = ref<number | null>(Number(sessionStorage.getItem('userId')) || null)
  const username = ref<string>(sessionStorage.getItem('username') || '')

  async function doLogin(name: string, password: string, captcha: string) {
    const res = await authApi.login(name, password, captcha)
    if (res.data.code === 200) {
      const u = res.data.data
      userId.value = u.id
      username.value = u.username
      sessionStorage.setItem('userId', String(u.id))
      sessionStorage.setItem('username', u.username)
    }
    return res.data
  }

  async function doRegister(name: string, password: string, confirm: string, captcha: string) {
    const res = await authApi.register(name, password, confirm, captcha)
    return res.data
  }

  async function doLogout() {
    userId.value = null
    username.value = ''
    sessionStorage.clear()
    try { await authApi.logout() } catch { /* ignore */ }
  }

  return { userId, username, doLogin, doRegister, doLogout }
})
