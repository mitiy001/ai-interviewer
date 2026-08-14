<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { SkillApi } from '@/api'
import type { Skill } from '@/api/types'

const list = ref<Skill[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const showDialog = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)

interface DimensionForm {
  name: string
  max: number
}

const emptyForm = {
  name: '',
  position: '',
  level: 'mid',
  type: 'TECH',
  promptTemplate: '',
  scoringDimensions: [] as DimensionForm[],
}

const form = reactive<{ name: string; position: string; level: string; type: string; promptTemplate: string; scoringDimensions: DimensionForm[] }>({ ...emptyForm })

const levelOptions = [
  { value: 'junior', label: '初级' },
  { value: 'mid', label: '中级' },
  { value: 'senior', label: '高级' },
]

function clearMsg() {
  errorMsg.value = ''
  successMsg.value = ''
}

async function loadList() {
  loading.value = true
  clearMsg()
  try {
    list.value = await SkillApi.list()
  } catch (e: any) {
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.position = ''
  form.level = 'mid'
  form.type = 'TECH'
  form.promptTemplate = ''
  form.scoringDimensions = []
  showDialog.value = true
  clearMsg()
}

function openEdit(row: Skill) {
  editingId.value = row.id
  form.name = row.name
  form.position = row.position
  form.level = row.level
  form.type = row.type || 'TECH'
  form.promptTemplate = row.promptTemplate
  form.scoringDimensions = (row.scoringDimensions || []).map((d) => ({ name: d.name, max: d.max }))
  showDialog.value = true
  clearMsg()
}

function closeDialog() {
  showDialog.value = false
}

function addDimension() {
  form.scoringDimensions.push({ name: '', max: 20 })
}

function removeDimension(index: number) {
  form.scoringDimensions.splice(index, 1)
}

async function submit() {
  clearMsg()
  if (!form.name.trim()) { errorMsg.value = '请输入 Skill 名称'; return }
  if (!form.position.trim()) { errorMsg.value = '请输入职位'; return }
  if (!form.promptTemplate.trim()) { errorMsg.value = '请输入提示词模板'; return }

  const validDims = form.scoringDimensions.filter((d) => d.name.trim())
  if (validDims.length === 0) { errorMsg.value = '请至少添加一个评分维度'; return }

  submitting.value = true
  try {
    const req = {
      name: form.name.trim(),
      position: form.position.trim(),
      level: form.level,
      type: form.type,
      promptTemplate: form.promptTemplate.trim(),
      scoringDimensions: validDims,
    }
    if (editingId.value === null) {
      await SkillApi.create(req)
      successMsg.value = '创建成功'
    } else {
      await SkillApi.update(editingId.value, req)
      successMsg.value = '更新成功'
    }
    showDialog.value = false
    await loadList()
  } catch (e: any) {
    errorMsg.value = e.message || '提交失败'
  } finally {
    submitting.value = false
  }
}

async function activate(row: Skill) {
  clearMsg()
  try {
    await SkillApi.activate(row.id)
    successMsg.value = `已激活「${row.name}」`
    await loadList()
  } catch (e: any) {
    errorMsg.value = e.message || '激活失败'
  }
}

async function remove(row: Skill) {
  if (!confirm(`确认删除「${row.name}」？`)) return
  clearMsg()
  try {
    await SkillApi.delete(row.id)
    successMsg.value = '删除成功'
    await loadList()
  } catch (e: any) {
    errorMsg.value = e.message || '删除失败'
  }
}

const levelLabel: Record<string, string> = { junior: '初级', mid: '中级', senior: '高级' }

onMounted(loadList)
</script>

<template>
  <div class="col">
    <div class="card">
      <div class="row" style="justify-content: space-between;">
        <h2 class="section-title" style="margin: 0;">Skill 判定标准</h2>
        <button class="btn" @click="openCreate">+ 新增 Skill</button>
      </div>
      <p class="desc muted" style="margin-top: 6px; font-size: 12px;">
        配置面试官判定标准，包括评分维度和提示词模板。激活的 Skill 将用于面试评分。
      </p>
      <p v-if="errorMsg" class="error-text" style="margin-top: 8px;">{{ errorMsg }}</p>
      <p v-if="successMsg" class="success-text" style="margin-top: 8px;">{{ successMsg }}</p>

      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="list.length === 0" class="empty">暂无 Skill，点击「新增 Skill」创建。</div>
      <div v-else class="table-wrap" style="margin-top: 12px;">
        <table class="table">
          <thead>
            <tr>
              <th style="width: 40px;">ID</th>
              <th>名称</th>
              <th>职位</th>
              <th>等级</th>
              <th>类型</th>
              <th>评分维度</th>
              <th>状态</th>
              <th style="width: 180px;">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in list" :key="row.id">
              <td class="muted">{{ row.id }}</td>
              <td>{{ row.name }}</td>
              <td>{{ row.position }}</td>
              <td>{{ levelLabel[row.level] || row.level }}</td>
              <td><span class="badge">{{ row.type === 'HR' ? '人事面' : '技术面' }}</span></td>
              <td class="muted" style="font-size: 11px; white-space: normal;">
                {{ (row.scoringDimensions || []).map((d) => `${d.name}(${d.max}分)`).join('、') }}
              </td>
              <td>
                <span v-if="row.isActive === 1" class="badge badge-success">已激活</span>
                <span v-else class="badge badge-warning">未激活</span>
              </td>
              <td>
                <div class="row" style="gap: 6px;">
                  <button v-if="row.isActive !== 1" class="btn btn-secondary" @click="activate(row)">激活</button>
                  <button class="btn btn-secondary" @click="openEdit(row)">编辑</button>
                  <button class="btn btn-danger" @click="remove(row)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="showDialog" class="modal-mask" @click.self="closeDialog">
      <div class="modal card">
        <h3 class="section-title" style="margin-bottom: 16px;">
          {{ editingId === null ? '新增 Skill' : '编辑 Skill' }}
        </h3>
        <p v-if="errorMsg" class="error-text" style="margin-bottom: 12px;">{{ errorMsg }}</p>

        <div class="form-group">
          <label>名称 *</label>
          <input v-model="form.name" class="input" placeholder="例如：Java 中级工程师面试官" />
        </div>

        <div class="form-group">
          <label>职位 *</label>
          <input v-model="form.position" class="input" placeholder="例如：java" />
        </div>

        <div class="form-group">
          <label>等级 *</label>
          <select v-model="form.level" class="input">
            <option v-for="opt in levelOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>

        <div class="form-group">
          <label>类型</label>
          <div class="row" style="gap: 8px;">
            <button
              class="btn"
              :class="form.type === 'TECH' ? 'btn-gradient' : 'btn-secondary'"
              style="flex: 1; padding: 8px 12px;"
              @click="form.type = 'TECH'"
            >技术面</button>
            <button
              class="btn"
              :class="form.type === 'HR' ? 'btn-gradient' : 'btn-secondary'"
              style="flex: 1; padding: 8px 12px;"
              @click="form.type = 'HR'"
            >人事面</button>
          </div>
        </div>

<!-- 评分维度 -->
        <div class="form-group">
          <div class="dim-header">
            <label>评分维度</label>
            <button class="btn btn-secondary btn-xs" @click="addDimension">+ 添加维度</button>
          </div>
          <div v-if="form.scoringDimensions.length === 0" class="muted" style="font-size: 12px; margin-top: 4px;">
            暂无维度，点击「添加维度」配置。
          </div>
          <div
            v-for="(dim, idx) in form.scoringDimensions"
            :key="idx"
            class="dim-row"
          >
            <input
              v-model="dim.name"
              class="input dim-input"
              placeholder="维度名称，如：技术准确性"
            />
            <input
              v-model.number="dim.max"
              type="number"
              min="1"
              max="100"
              class="input dim-max"
              placeholder="满分"
            />
            <span class="muted" style="font-size: 12px;">分</span>
            <button class="btn btn-danger btn-xs" @click="removeDimension(idx)">删除</button>
          </div>
        </div>

        <div class="form-group">
          <label>提示词模板 *</label>
          <textarea
            v-model="form.promptTemplate"
            class="input textarea"
            rows="8"
            placeholder="编写 AI 面试官的提示词模板，包含评分维度和判定要点..."
          ></textarea>
        </div>

        <div class="row" style="justify-content: flex-end; margin-top: 8px; gap: 8px;">
          <button class="btn btn-secondary" @click="closeDialog">取消</button>
          <button class="btn" :disabled="submitting" @click="submit">
            {{ submitting ? '提交中…' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  background: var(--overlay-mask);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  padding: 20px;
}

.modal {
  width: 680px;
  max-width: 100%;
  max-height: 90vh;
  overflow-y: auto;
}

.dim-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.dim-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.dim-input {
  flex: 1;
}

.dim-max {
  width: 80px;
  flex-shrink: 0;
}

.btn-xs {
  font-size: 11px;
  padding: 4px 10px;
}

.textarea {
  resize: vertical;
  font-family: var(--font-mono, 'SFMono-Regular', Consolas, monospace);
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .modal {
    width: 94vw;
    max-height: 94dvh;
    padding: 16px;
  }
  .modal-mask {
    padding: 0;
  }
  .dim-row {
    flex-wrap: wrap;
  }
  .dim-max {
    width: 70px;
  }
}

@media (max-width: 480px) {
  .modal {
    width: 100vw;
    max-height: 100dvh;
    border-radius: 0;
    padding: 14px;
  }
}
</style>