import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      meta: { guest: true },
      component: () => import('@/views/Login.vue'),
    },
    { path: '/', name: 'home', component: () => import('@/views/Home.vue') },
    {
      path: '/upload',
      name: 'upload',
      component: () => import('@/views/Upload.vue'),
    },
    {
      path: '/interview',
      name: 'interview',
      component: () => import('@/views/Interview.vue'),
    },
    {
      path: '/report',
      name: 'report',
      component: () => import('@/views/Report.vue'),
    },
    {
      path: '/practice',
      name: 'practice',
      component: () => import('@/views/Practice.vue'),
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/Settings.vue'),
    },
    {
      path: '/admin/users',
      name: 'admin-users',
      meta: { admin: true },
      component: () => import('@/views/UserManage.vue'),
    },
    {
      path: '/admin/skills',
      name: 'admin-skills',
      meta: { admin: true },
      component: () => import('@/views/SkillManage.vue'),
    },
  ],
})

// 路由守卫：未登录用户只能访问 guest 页面
router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // 尝试获取用户信息（如果 Cookie 有效则自动恢复登录状态）
  if (!auth.user) {
    await auth.fetchUser()
  }

  // 未登录且目标页面不是 guest 页 → 跳转登录
  if (!auth.user && !to.meta.guest) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录用户访问 guest 页 → 跳转首页
  if (auth.user && to.meta.guest) {
    return { path: '/' }
  }

  // 非管理员访问 admin 页面 → 跳转首页
  if (to.meta.admin && !auth.isAdmin) {
    return { path: '/' }
  }
})

export default router