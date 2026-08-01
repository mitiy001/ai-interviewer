<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const username = ref('')
const password = ref('')
const captchaToken = ref('')
const captchaCode = ref('')
const captchaImage = ref('')
const errorMsg = ref('')
const submitting = ref(false)

async function loadCaptcha() {
  const result = await auth.getCaptcha()
  captchaToken.value = result.token
  captchaImage.value = result.image
}

onMounted(() => {
  loadCaptcha()
})

async function handleLogin() {
  if (!username.value || !password.value || !captchaCode.value) {
    errorMsg.value = '请填写所有字段'
    return
  }
  submitting.value = true
  errorMsg.value = ''
  try {
    await auth.login(username.value, password.value, captchaToken.value, captchaCode.value)
    router.push('/')
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || e.message || '登录失败'
    loadCaptcha()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h1 class="auth-title">登录</h1>
      <p class="auth-subtitle">AI 面试官</p>

      <form @submit.prevent="handleLogin" class="auth-form">
        <div class="form-group">
          <label>用户名</label>
          <input
            v-model="username"
            type="text"
            placeholder="请输入用户名"
            autocomplete="username"
            maxlength="20"
          />
        </div>

        <div class="form-group">
          <label>密码</label>
          <input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </div>

        <div class="form-group">
          <label>验证码</label>
          <div class="captcha-row">
            <input
              v-model="captchaCode"
              type="text"
              placeholder="输入验证码"
              maxlength="4"
            />
            <img
              v-if="captchaImage"
              :src="captchaImage"
              class="captcha-img"
              @click="loadCaptcha"
              title="点击刷新"
            />
          </div>
        </div>

        <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>

        <button type="submit" class="btn-primary" :disabled="submitting">
          {{ submitting ? '登录中...' : '登录' }}
        </button>
      </form>

      <p class="auth-link">
        请联系管理员创建账号
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: calc(100vh - 64px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 40px 32px;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.08);
}

.auth-title {
  font-size: 24px;
  font-weight: 700;
  text-align: center;
  margin: 0 0 4px;
}

.auth-subtitle {
  text-align: center;
  color: #888;
  font-size: 14px;
  margin: 0 0 32px;
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-size: 13px;
  font-weight: 500;
  color: #555;
}

.form-group input {
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.form-group input:focus {
  border-color: #4f6ef7;
}

.captcha-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.captcha-row input {
  flex: 1;
}

.captcha-img {
  height: 44px;
  width: auto;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid #eee;
}

.error-msg {
  color: #e74c3c;
  font-size: 13px;
  text-align: center;
  margin: 0;
}

.btn-primary {
  width: 100%;
  padding: 12px;
  background: #4f6ef7;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-primary:hover {
  background: #3d5bd9;
}

.btn-primary:disabled {
  background: #a0b0f0;
  cursor: not-allowed;
}

.auth-link {
  text-align: center;
  font-size: 13px;
  color: #888;
  margin-top: 24px;
}

.auth-link a {
  color: #4f6ef7;
  text-decoration: none;
  font-weight: 500;
}

.auth-link a:hover {
  text-decoration: underline;
}
</style>