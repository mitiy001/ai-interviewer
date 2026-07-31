import axios from 'axios'

// 生产环境使用 VITE_API_BASE_URL 环境变量，开发环境使用 Vite proxy
const API_BASE = import.meta.env.VITE_API_BASE_URL
  ? `${import.meta.env.VITE_API_BASE_URL}/api`
  : '/api'

const http = axios.create({
  baseURL: API_BASE,
  timeout: 30000,
  withCredentials: true,
})

// 统一处理后端 Result<T> 结构
http.interceptors.response.use(
  (response) => {
    const data = response.data
    if (data && typeof data.code !== 'undefined' && data.code !== 200) {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      // 未登录或登录过期
      // 排除 /auth/me：路由守卫中调用 fetchUser 时 401 是正常情况（未登录用户访问 guest 页）
      // 由路由守卫自己处理跳转，避免 guest 页面被误跳到登录页
      const url = error.config?.url || ''
      if (!url.includes('/auth/me')) {
        // 其他接口 401 说明 session 已过期，跳转登录
        import('@/router').then(({ default: router }) => {
          router.push('/login').catch(() => {})
        })
      }
    }
    return Promise.reject(error)
  }
)

export default http
