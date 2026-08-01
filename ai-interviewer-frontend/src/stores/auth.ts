import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import http from '@/api/http'

export interface UserInfo {
  id: number
  username: string
  role: string
  lastLoginAt: string | null
  createdAt: string
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const token = ref<string | null>(null)
  const loading = ref(false)

  const isAdmin = computed(() => user.value?.role === 'admin')

  /** 登录 */
  async function login(
    username: string,
    password: string
  ) {
    loading.value = true
    try {
      const res = await http.post('/auth/login', {
        username,
        password,
      })
      const data = res.data.data
      token.value = data.token
      user.value = data.user
      return true
    } finally {
      loading.value = false
    }
  }

  /** 获取当前用户信息 */
  async function fetchUser() {
    try {
      const res = await http.get('/auth/me')
      user.value = res.data.data
      return true
    } catch {
      user.value = null
      token.value = null
      return false
    }
  }

  /** 登出 */
  async function logout() {
    await http.post('/auth/logout')
    user.value = null
    token.value = null
  }

  /** 管理员创建用户 */
  async function createUser(username: string, password: string) {
    const res = await http.post('/auth/admin/users', { username, password })
    return res.data
  }

  return { user, token, loading, isAdmin, login, fetchUser, logout, createUser }
})
