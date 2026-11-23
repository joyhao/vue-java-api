import { createRouter, createWebHistory } from 'vue-router'
import request from '@/api/request'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/register', name: 'register', component: () => import('@/views/LoginView.vue') },
    { path: '/users', name: 'users', component: () => import('@/views/UsersView.vue') },
    { path: '/videos', name: 'videos', component: () => import('@/views/VideosView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  if (to.name === 'login' || to.name === 'register') return true
  try {
    await request.get('/auth/me')
    return true
  } catch {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
})

export default router
