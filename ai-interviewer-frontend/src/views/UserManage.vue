<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuthStore, type UserInfo } from '@/stores/auth'

const auth = useAuthStore()

// ===== 用户列表 =====
const users = ref<UserInfo[]>([])
const loadingList = ref(false)
const listError = ref('')

async function loadUsers() {
  loadingList.value = true
  listError.value = ''
  try {
    users.value = await auth.listUsers()
  } catch (e: any) {
    listError.value = e.response?.data?.message || e.message || '加载失败'
  } finally {
    loadingList.value = false
  }
}

// ===== 创建用户 =====
const createUsername = ref('')
const createPassword = ref('')
const createError = ref('')
const createSuccess = ref('')
const creating = ref(false)

const passwordMinLen = 8
const passwordHint = `至少 ${passwordMinLen} 位，包含字母和数字`

function validatePassword(v: string) {
  return /^(?=.*[a-zA-Z])(?=.*\d).{8,}$/.test(v)
}

async function handleCreate() {
  createError.value = ''
  createSuccess.value = ''

  const u = createUsername.value.trim()
  const p = createPassword.value

  if (!u || u.length < 2) {
    createError.value = '用户名至少 2 个字符'
    return
  }
  if (!/^[a-zA-Z0-9_]{2,20}$/.test(u)) {
    createError.value = '用户名只能包含字母、数字、下划线（2-20 位）'
    return
  }
  if (!validatePassword(p)) {
    createError.value = passwordHint
    return
  }

  creating.value = true
  try {
    await auth.createUser(u, p)
    createSuccess.value = `用户「${u}」创建成功`
    createUsername.value = ''
    createPassword.value = ''
    await loadUsers()
  } catch (e: any) {
    createError.value = e.response?.data?.message || e.message || '创建失败'
  } finally {
    creating.value = false
  }
}

// ===== 编辑用户 =====
const editingUser = ref<UserInfo | null>(null)
const editStatus = ref<number>(1)
const editRole = ref<string>('user')
const editError = ref('')
const editSuccess = ref('')
const saving = ref(false)

function openEdit(user: UserInfo) {
  editingUser.value = user
  editStatus.value = user.status
  editRole.value = user.role
  editError.value = ''
  editSuccess.value = ''
}

function closeEdit() {
  editingUser.value = null
}

async function handleEdit() {
  if (!editingUser.value) return
  editError.value = ''
  editSuccess.value = ''
  saving.value = true
  try {
    await auth.updateUser(editingUser.value.id, {
      status: editStatus.value,
      role: editRole.value,
    })
    editSuccess.value = '更新成功'
    await loadUsers()
    setTimeout(closeEdit, 1000)
  } catch (e: any) {
    editError.value = e.response?.data?.message || e.message || '更新失败'
  } finally {
    saving.value = false
  }
}

// ===== 删除用户 =====
const deletingId = ref<number | null>(null)
const deleteError = ref('')

async function confirmDelete(id: number, username: string) {
  if (!confirm(`确定要删除用户「${username}」吗？此操作不可恢复。`)) return
  deleteError.value = ''
  try {
    await auth.deleteUser(id)
    await loadUsers()
  } catch (e: any) {
    deleteError.value = e.response?.data?.message || e.message || '删除失败'
  }
}

// ===== 重置密码 =====
const resetUserId = ref<number | null>(null)
const resetUsername = ref('')
const resetPassword = ref('')
const resetError = ref('')
const resetSuccess = ref('')
const resetting = ref(false)

function openReset(user: UserInfo) {
  resetUserId.value = user.id
  resetUsername.value = user.username
  resetPassword.value = ''
  resetError.value = ''
  resetSuccess.value = ''
}

function closeReset() {
  resetUserId.value = null
  resetUsername.value = ''
}

async function handleReset() {
  if (resetUserId.value === null) return
  resetError.value = ''
  resetSuccess.value = ''

  if (!validatePassword(resetPassword.value)) {
    resetError.value = passwordHint
    return
  }

  resetting.value = true
  try {
    await auth.resetPassword(resetUserId.value, resetPassword.value)
    resetSuccess.value = '密码重置成功'
    setTimeout(closeReset, 1000)
  } catch (e: any) {
    resetError.value = e.response?.data?.message || e.message || '重置失败'
  } finally {
    resetting.value = false
  }
}

// ===== 生命周期 =====
onMounted(loadUsers)
</script>

<template>
  <div class="admin-page">
    <!-- 用户列表 -->
    <div class="admin-card">
      <h1 class="admin-title">用户管理</h1>
      <p class="admin-subtitle">共 {{ users.length }} 个用户</p>

      <p v-if="listError" class="msg-error">{{ listError }}</p>

      <div v-if="loadingList" class="loading-text">加载中...</div>

      <div v-else class="table-wrap">
        <table class="user-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>用户名</th>
              <th>角色</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>最后登录</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.id">
              <td>{{ u.id }}</td>
              <td>{{ u.username }}</td>
              <td>
                <span :class="u.role === 'admin' ? 'badge badge-admin' : 'badge badge-user'">
                  {{ u.role === 'admin' ? '管理员' : '用户' }}
                </span>
              </td>
              <td>
                <span :class="u.status === 1 ? 'badge badge-active' : 'badge badge-disabled'">
                  {{ u.status === 1 ? '正常' : '禁用' }}
                </span>
              </td>
              <td class="muted">{{ u.createdAt?.slice(0, 16).replace('T', ' ') }}</td>
              <td class="muted">{{ u.lastLoginAt?.slice(0, 16).replace('T', ' ') || '-' }}</td>
              <td>
                <div class="action-btns">
                  <button class="btn-sm btn-edit" @click="openEdit(u)" :disabled="u.id === auth.user?.id">编辑</button>
                  <button class="btn-sm btn-reset" @click="openReset(u)">重置密码</button>
                  <button class="btn-sm btn-delete" @click="confirmDelete(u.id, u.username)" :disabled="u.id === auth.user?.id">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="deleteError" class="msg-error">{{ deleteError }}</p>
    </div>

    <!-- 创建用户 -->
    <div class="admin-card">
      <h2 class="admin-title" style="font-size: 18px;">创建新用户</h2>

      <form @submit.prevent="handleCreate" class="admin-form">
        <div class="form-group">
          <label>用户名</label>
          <input
            v-model="createUsername"
            type="text"
            placeholder="2-20 位字母、数字或下划线"
            autocomplete="off"
            maxlength="20"
          />
        </div>

        <div class="form-group">
          <label>密码</label>
          <input
            v-model="createPassword"
            type="password"
            :placeholder="passwordHint"
            autocomplete="new-password"
          />
          <p class="form-hint">{{ passwordHint }}</p>
        </div>

        <p v-if="createError" class="msg-error">{{ createError }}</p>
        <p v-if="createSuccess" class="msg-success">{{ createSuccess }}</p>

        <button type="submit" class="btn-primary" :disabled="creating">
          {{ creating ? '创建中...' : '创建用户' }}
        </button>
      </form>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="editingUser" class="modal-overlay" @click.self="closeEdit">
      <div class="modal-card">
        <h3>编辑用户 - {{ editingUser.username }}</h3>

        <div class="form-group">
          <label>状态</label>
          <select v-model="editStatus" class="input">
            <option :value="1">正常</option>
            <option :value="0">禁用</option>
          </select>
        </div>

        <div class="form-group">
          <label>角色</label>
          <select v-model="editRole" class="input">
            <option value="user">用户</option>
            <option value="admin">管理员</option>
          </select>
        </div>

        <p v-if="editError" class="msg-error">{{ editError }}</p>
        <p v-if="editSuccess" class="msg-success">{{ editSuccess }}</p>

        <div class="modal-actions">
          <button class="btn btn-secondary" @click="closeEdit" :disabled="saving">取消</button>
          <button class="btn btn-primary" @click="handleEdit" :disabled="saving">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 重置密码弹窗 -->
    <div v-if="resetUserId !== null" class="modal-overlay" @click.self="closeReset">
      <div class="modal-card">
        <h3>重置密码 - {{ resetUsername }}</h3>

        <div class="form-group">
          <label>新密码</label>
          <input
            v-model="resetPassword"
            type="password"
            :placeholder="passwordHint"
            autocomplete="new-password"
          />
          <p class="form-hint">{{ passwordHint }}</p>
        </div>

        <p v-if="resetError" class="msg-error">{{ resetError }}</p>
        <p v-if="resetSuccess" class="msg-success">{{ resetSuccess }}</p>

        <div class="modal-actions">
          <button class="btn btn-secondary" @click="closeReset" :disabled="resetting">取消</button>
          <button class="btn btn-primary" @click="handleReset" :disabled="resetting">
            {{ resetting ? '重置中...' : '确认重置' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.admin-card {
  background: #fff;
  border-radius: 12px;
  padding: 28px 32px;
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
  margin: 0 0 20px;
}

.admin-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
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

.form-group .input,
.form-group input {
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.2s;
}

.form-group .input:focus,
.form-group input:focus {
  border-color: #4f6ef7;
}

.form-hint {
  margin: 0;
  font-size: 12px;
  color: #999;
}

.loading-text {
  text-align: center;
  color: #888;
  padding: 24px;
}

.table-wrap {
  overflow-x: auto;
}

.user-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.user-table th,
.user-table td {
  padding: 10px 8px;
  text-align: left;
  border-bottom: 1px solid #eee;
  white-space: nowrap;
}

.user-table th {
  font-weight: 600;
  color: #555;
  background: #f9fafb;
  position: sticky;
  top: 0;
}

.user-table tbody tr:hover {
  background: #f5f7fa;
}

.muted {
  color: #999;
}

.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.badge-admin {
  background: #eef2ff;
  color: #4f46e5;
}

.badge-user {
  background: #f0fdf4;
  color: #16a34a;
}

.badge-active {
  background: #f0fdf4;
  color: #16a34a;
}

.badge-disabled {
  background: #fef2f2;
  color: #dc2626;
}

.action-btns {
  display: flex;
  gap: 4px;
}

.btn-sm {
  padding: 3px 8px;
  font-size: 11px;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-sm:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.btn-edit:hover {
  border-color: #4f6ef7;
  color: #4f6ef7;
}

.btn-reset:hover {
  border-color: #d97706;
  color: #d97706;
}

.btn-delete:hover {
  border-color: #dc2626;
  color: #dc2626;
}

.msg-error {
  color: #dc2626;
  font-size: 13px;
  text-align: center;
  margin: 8px 0 0;
}

.msg-success {
  color: #16a34a;
  font-size: 13px;
  text-align: center;
  margin: 8px 0 0;
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

.btn {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-secondary {
  background: #fff;
  border: 1px solid #ddd;
  color: #555;
}

.btn-secondary:hover {
  border-color: #bbb;
  background: #f5f5f5;
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-card {
  background: #fff;
  border-radius: 12px;
  padding: 28px 24px;
  width: 380px;
  max-width: 90vw;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}

.modal-card h3 {
  margin: 0 0 20px;
  font-size: 16px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
}

select.input {
  appearance: auto;
}
</style>