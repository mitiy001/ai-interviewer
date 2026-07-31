import { defineStore } from 'pinia'
import { ref } from 'vue'
import http from '@/api/http'

export interface UserInfo {
  id: number
  username: string
  lastLoginAt: string | null
  createdAt: string
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const token = ref<string | null>(null)
  const loading = ref(false)

  /** 获取验证码 */
  async function getCaptcha(): Promise<{ token: string; image: string }> {
    const res = await http.get('/auth/captcha')
    return res.data.data
  }

  /** 登录 */
  async function login(
    username: string,
    password: string,
    captchaToken: string,
    captchaCode: string
  ) {
    loading.value = true
    try {
      const res = await http.post('/auth/login', {
        username,
        password,
        captchaToken,
        captchaCode,
      })
      const data = res.data.data
      token.value = data.token
      user.value = data.user
      return true
    } finally {
      loading.value = false
    }
  }

  /** 注册（注册后自动登录） */
  async function register(
    username: string,
    password: string,
    captchaToken: string,
    captchaCode: string
  ) {
    loading.value = true
    try {
      const res = await http.post('/auth/register', {
        username,
        password,
        captchaToken,
        captchaCode,
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

  return { user, token, loading, getCaptcha, login, register, fetchUser, logout }
})
