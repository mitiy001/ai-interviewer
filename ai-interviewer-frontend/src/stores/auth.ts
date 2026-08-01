import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import http from '@/api/http'

export interface UserInfo {
  id: number
  username: string
  role: string
  status: number
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

  /** 管理员获取用户列表 */
  async function listUsers(): Promise<UserInfo[]> {
    const res = await http.get('/auth/admin/users')
    return res.data.data
  }

  /** 管理员更新用户（status/role） */
  async function updateUser(id: number, data: { status?: number; role?: string }) {
    await http.put(`/auth/admin/users/${id}`, data)
  }

  /** 管理员删除用户 */
  async function deleteUser(id: number) {
    await http.delete(`/auth/admin/users/${id}`)
  }

  /** 管理员重置用户密码 */
  async function resetPassword(id: number, password: string) {
    await http.put(`/auth/admin/users/${id}/reset-password`, { password })
  }

  return { user, token, loading, isAdmin, login, fetchUser, logout, createUser, listUsers, updateUser, deleteUser, resetPassword }
})