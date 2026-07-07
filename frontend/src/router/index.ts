import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/kb' },
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { noHeader: true } },
    { path: '/register', component: () => import('@/views/RegisterView.vue'), meta: { noHeader: true } },
    { path: '/kb', component: () => import('@/views/KbListView.vue'), meta: { requiresAuth: true } },
    { path: '/kb/:id', component: () => import('@/views/KbDetailView.vue'), meta: { requiresAuth: true } },
    { path: '/chat/:kbId', component: () => import('@/views/ChatView.vue'), meta: { requiresAuth: true } },
  ]
})

router.beforeEach((to, _from, next) => {
  const userId = sessionStorage.getItem('userId')
  if (to.meta.requiresAuth && !userId) {
    next('/login')
  } else {
    next()
  }
})

export default router
