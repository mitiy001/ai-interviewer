<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const username = ref('')
const password = ref('')
const errorMsg = ref('')
const successMsg = ref('')
const submitting = ref(false)

const passwordMinLen = 8
const passwordHint = `至少 ${passwordMinLen} 位，包含字母和数字`

function validatePassword(v: string) {
  return /^(?=.*[a-zA-Z])(?=.*\d).{8,}$/.test(v)
}

async function handleCreate() {
  errorMsg.value = ''
  successMsg.value = ''

  const u = username.value.trim()
  const p = password.value

  if (!u || u.length < 2) {
    errorMsg.value = '用户名至少 2 个字符'
    return
  }
  if (!/^[a-zA-Z0-9_]{2,20}$/.test(u)) {
    errorMsg.value = '用户名只能包含字母、数字、下划线（2-20 位）'
    return
  }
  if (!validatePassword(p)) {
    errorMsg.value = passwordHint
    return
  }

  submitting.value = true
  try {
    await auth.createUser(u, p)
    successMsg.value = `用户「${u}」创建成功`
    username.value = ''
    password.value = ''
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || e.message || '创建失败'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="admin-page">
    <div class="admin-card">
      <h1 class="admin-title">用户管理</h1>
      <p class="admin-subtitle">创建新的用户账号</p>

      <form @submit.prevent="handleCreate" class="admin-form">
        <div class="form-group">
          <label>用户名</label>
          <input
            v-model="username"
            type="text"
            placeholder="2-20 位字母、数字或下划线"
            autocomplete="off"
            maxlength="20"
          />
        </div>

        <div class="form-group">
          <label>密码</label>
          <input
            v-model="password"
            type="password"
            :placeholder="passwordHint"
            autocomplete="new-password"
          />
          <p class="form-hint">{{ passwordHint }}</p>
        </div>

        <p v-if="errorMsg" class="msg-error">{{ errorMsg }}</p>
        <p v-if="successMsg" class="msg-success">{{ successMsg }}</p>

        <button type="submit" class="btn-primary" :disabled="submitting">
          {{ submitting ? '创建中...' : '创建用户' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.admin-page {
  min-height: calc(100vh - 64px);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 48px 24px;
}

.admin-card {
  width: 100%;
  max-width: 440px;
  background: #fff;
  border-radius: 12px;
  padding: 40px 32px;
  box-shadow: 0 2px 16px rgba(0, 0, 0, 0.08);
}

.admin-title {
  font-size: 22px;
  font-weight: 700;
  margin: 0 0 4px;
}

.admin-subtitle {
  color: #888;
  font-size: 14px;
  margin: 0 0 28px;
}

.admin-form {
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

.form-hint {
  margin: 0;
  font-size: 12px;
  color: #999;
}

.msg-error {
  color: #e74c3c;
  font-size: 13px;
  text-align: center;
  margin: 0;
}

.msg-success {
  color: #27ae60;
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
</style>