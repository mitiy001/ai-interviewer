<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ModelConfigApi } from '@/api'
import type { ModelConfig, ModelConfigReq, ModelTestResult } from '@/api/types'
import {
  ttsSupported,
  ttsSettings,
  ttsVoiceList,
  setVoice,
  setRate,
  setPitch,
  setVolume,
  speakPreview as ttsSpeak,
  cancel as ttsCancel,
  preloadVoices,
} from '@/utils/tts'

const list = ref<ModelConfig[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const showDialog = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)

// 连通测试状态
const testingId = ref<number | null>(null)
const testingForm = ref(false)
const testResult = ref<ModelTestResult | null>(null)

// TTS 试听状态
const previewing = ref(false)

const emptyForm: ModelConfigReq = {
  id: undefined,
  name: '',
  provider: 'openai',
  apiKey: '',
  model: '',
  endpoint: '',
  judgeModel: '',
  judgeEndpoint: '',
  isActive: 0,
}

const form = reactive<ModelConfigReq>({ ...emptyForm })

const providers = [
  { value: 'openai', label: 'OpenAI' },
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'qwen', label: '通义千问' },
  { value: 'doubao', label: '豆包' },
  { value: 'custom', label: '自定义（OpenAI 兼容）' },
]

/** 中文音色列表（reactive，音色异步加载后自动更新） */
const zhVoices = computed(() => {
  const zh = ttsVoiceList.value.filter((v) => v.lang.startsWith('zh'))
  return zh.length > 0 ? zh : ttsVoiceList.value
})

/** 默认试听文案 */
const PREVIEW_TEXT = '你好，我是 AI 面试官，欢迎参加本次面试，请做好准备。'

function clearMsg() {
  errorMsg.value = ''
  successMsg.value = ''
}

async function loadList() {
  loading.value = true
  clearMsg()
  try {
    list.value = await ModelConfigApi.list()
  } catch (e: any) {
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { ...emptyForm })
  testResult.value = null
  showDialog.value = true
  clearMsg()
}

function openEdit(row: ModelConfig) {
  editingId.value = row.id
  Object.assign(form, {
    id: row.id,
    name: row.name,
    provider: row.provider,
    // 回填脱敏值，让用户看到已配置；提交时若未修改（仍为脱敏值）后端会保留原 Key
    apiKey: row.apiKeyMasked || '',
    model: row.model,
    endpoint: row.endpoint,
    judgeModel: row.judgeModel || '',
    judgeEndpoint: row.judgeEndpoint || '',
    isActive: row.isActive,
  })
  testResult.value = null
  showDialog.value = true
  clearMsg()
}

function closeDialog() {
  showDialog.value = false
}

async function submit() {
  clearMsg()
  if (!form.name.trim()) {
    errorMsg.value = '请输入配置名称'
    return
  }
  if (!form.model.trim()) {
    errorMsg.value = '请输入模型名称'
    return
  }
  if (!editingId.value && !form.apiKey?.trim()) {
    errorMsg.value = '请输入 API Key'
    return
  }
  submitting.value = true
  try {
    if (editingId.value === null) {
      await ModelConfigApi.create(form)
      successMsg.value = '创建成功'
    } else {
      const req: ModelConfigReq = { ...form }
      if (!req.apiKey) delete req.apiKey
      await ModelConfigApi.update(editingId.value, req)
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

async function activate(row: ModelConfig) {
  clearMsg()
  try {
    await ModelConfigApi.activate(row.id)
    successMsg.value = `已激活「${row.name}」`
    await loadList()
  } catch (e: any) {
    errorMsg.value = e.message || '激活失败'
  }
}

async function remove(row: ModelConfig) {
  if (!confirm(`确认删除「${row.name}」？`)) return
  clearMsg()
  try {
    await ModelConfigApi.delete(row.id)
    successMsg.value = '删除成功'
    await loadList()
  } catch (e: any) {
    errorMsg.value = e.message || '删除失败'
  }
}

/** 测试列表中已保存配置 */
async function testRow(row: ModelConfig) {
  clearMsg()
  testingId.value = row.id
  try {
    const r = await ModelConfigApi.testSaved(row.id)
    if (r.success) {
      successMsg.value = `「${row.name}」连通成功（${r.latencyMs}ms）：${r.reply}`
    } else {
      errorMsg.value = `「${row.name}」连通失败：${r.message}`
    }
  } catch (e: any) {
    errorMsg.value = e.message || '测试请求失败'
  } finally {
    testingId.value = null
  }
}

/** 测试弹窗中的未保存表单 */
async function testForm() {
  clearMsg()
  if (!form.model.trim()) {
    errorMsg.value = '请先填写模型名称'
    return
  }
  // 编辑场景：apiKey 可为脱敏值（后端回查库补全）；新建场景：apiKey 必填且非脱敏
  if (editingId.value === null && !form.apiKey?.trim()) {
    errorMsg.value = '请先填写 API Key'
    return
  }
  testingForm.value = true
  testResult.value = null
  try {
    const r = await ModelConfigApi.testUnsaved({ ...form })
    testResult.value = r
  } catch (e: any) {
    testResult.value = {
      success: false,
      message: e.message || '测试请求失败',
      reply: '',
      latencyMs: 0,
    }
  } finally {
    testingForm.value = false
  }
}

/** TTS 声音试听：直接用浏览器内置语音包朗读 */
function previewVoice() {
  if (!ttsSupported) return
  ttsCancel()
  previewing.value = true
  // speak 是异步的，这里简单用 setTimeout 标记结束（实际播放由浏览器控制）
  ttsSpeak(PREVIEW_TEXT)
  // 粗略估算播放时长（按字数 * 语速），用于恢复按钮状态
  const estMs = Math.max(2000, PREVIEW_TEXT.length * 300 / ttsSettings.value.rate)
  setTimeout(() => { previewing.value = false }, estMs)
}

function stopPreview() {
  ttsCancel()
  previewing.value = false
}

onMounted(async () => {
  await loadList()
  preloadVoices()
})
onUnmounted(stopPreview)
</script>

<template>
  <div class="col">
    <div class="card">
      <div class="row" style="justify-content: space-between;">
        <h2 class="section-title" style="margin: 0;">模型配置</h2>
        <button class="btn" @click="openCreate">+ 新增配置</button>
      </div>
      <p v-if="errorMsg" class="error-text" style="margin-top: 8px;">{{ errorMsg }}</p>
      <p v-if="successMsg" class="success-text" style="margin-top: 8px;">{{ successMsg }}</p>

      <div v-if="loading" class="empty">加载中…</div>
      <div v-else-if="list.length === 0" class="empty">暂无配置，点击「新增配置」创建。</div>
      <div v-else class="table-wrap" style="margin-top: 12px;">
      <table class="table">
        <thead>
          <tr>
            <th>名称</th>
            <th>Provider</th>
            <th>模型</th>
            <th>Endpoint</th>
            <th>API Key</th>
            <th>状态</th>
            <th style="width: 200px;">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in list" :key="row.id">
            <td>{{ row.name }}</td>
            <td>{{ row.provider }}</td>
            <td class="mono">{{ row.model }}</td>
            <td class="mono muted">{{ row.endpoint || '-' }}</td>
            <td class="mono muted">{{ row.apiKeyMasked || '-' }}</td>
            <td>
              <span v-if="row.isActive === 1" class="badge badge-success">已激活</span>
              <span v-else class="badge badge-warning">未激活</span>
            </td>
            <td>
              <div class="row" style="gap: 6px;">
                <button
                  class="btn btn-secondary"
                  :disabled="testingId === row.id"
                  @click="testRow(row)"
                >{{ testingId === row.id ? '测试中…' : '测试' }}</button>
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

    <!-- 新增 / 编辑弹窗 -->
    <div v-if="showDialog" class="modal-mask" @click.self="closeDialog">
      <div class="modal card">
        <h3 class="section-title" style="margin-bottom: 16px;">
          {{ editingId === null ? '新增模型配置' : '编辑模型配置' }}
        </h3>
        <p v-if="errorMsg" class="error-text" style="margin-bottom: 12px;">{{ errorMsg }}</p>

        <div class="form-group">
          <label>配置名称 *</label>
          <input v-model="form.name" class="input" placeholder="例如：默认 OpenAI" />
        </div>

        <div class="form-group">
          <label>Provider *</label>
          <select v-model="form.provider" class="input">
            <option v-for="p in providers" :key="p.value" :value="p.value">{{ p.label }}</option>
          </select>
        </div>

        <div class="form-group">
          <label>模型名称 *</label>
          <input v-model="form.model" class="input mono" placeholder="例如：gpt-4o / deepseek-chat" />
        </div>

        <div class="form-group">
          <label>Endpoint</label>
          <input v-model="form.endpoint" class="input mono" placeholder="https://api.openai.com/v1" />
          <small class="muted">OpenAI 兼容接口地址，留空使用默认</small>
        </div>

        <div class="form-group">
          <label>API Key {{ editingId === null ? '*' : '（已配置，无需重填）' }}</label>
          <input
            v-model="form.apiKey"
            type="text"
            class="input mono"
            :placeholder="editingId === null ? 'sk-...' : '修改请输入新的 API Key，留空则保持原 Key 不变'"
            autocomplete="off"
          />
        </div>

        <div class="form-group">
          <label>判定模型（可选）</label>
          <input v-model="form.judgeModel" class="input mono" placeholder="用于评分判定，留空则使用主模型" />
        </div>

        <div class="form-group">
          <label>判定模型 Endpoint（可选）</label>
          <input v-model="form.judgeEndpoint" class="input mono" placeholder="可使用不同 provider 做判定" />
        </div>

        <!-- ===== 语音播报配置（浏览器内置 TTS，零费用）===== -->
        <div class="tts-section">
          <div class="tts-section-head">
            <span class="tts-section-title">语音播报配置</span>
            <span class="tts-section-hint">浏览器内置语音包 · 零费用零配置</span>
          </div>

          <p v-if="!ttsSupported" class="muted" style="font-size: 12px;">
            当前浏览器不支持语音合成，请使用 Chrome / Edge / Safari。
          </p>

          <div v-else>
            <div class="form-group">
              <label>面试官音色</label>
              <select
                :value="ttsSettings.voiceURI"
                class="input"
                @change="setVoice(($event.target as HTMLSelectElement).value)"
              >
                <option value="">默认（自动选择最佳中文音色）</option>
                <option v-for="v in zhVoices" :key="v.voiceURI" :value="v.voiceURI">
                  {{ v.name }}（{{ v.lang }}）
                </option>
              </select>
              <small class="muted">
                共 {{ zhVoices.length }} 个音色。推荐晓晓（女声温柔）或云希（男声清朗）。
              </small>
            </div>

            <div class="form-group">
              <label>语速 <span class="muted">（{{ ttsSettings.rate.toFixed(2) }}x）</span></label>
              <input
                type="range"
                min="0.5"
                max="2.0"
                step="0.05"
                :value="ttsSettings.rate"
                class="tts-slider"
                @input="setRate(Number(($event.target as HTMLInputElement).value))"
              />
              <div class="tts-slider-marks">
                <span>慢</span><span>正常</span><span>快</span>
              </div>
            </div>

            <div class="form-group">
              <label>音调 <span class="muted">（{{ ttsSettings.pitch.toFixed(2) }}）</span></label>
              <input
                type="range"
                min="0.5"
                max="2.0"
                step="0.05"
                :value="ttsSettings.pitch"
                class="tts-slider"
                @input="setPitch(Number(($event.target as HTMLInputElement).value))"
              />
              <div class="tts-slider-marks">
                <span>低沉</span><span>自然</span><span>高亢</span>
              </div>
            </div>

            <div class="form-group">
              <label>音量 <span class="muted">（{{ Math.round(ttsSettings.volume * 100) }}%）</span></label>
              <input
                type="range"
                min="0"
                max="1"
                step="0.05"
                :value="ttsSettings.volume"
                class="tts-slider"
                @input="setVolume(Number(($event.target as HTMLInputElement).value))"
              />
              <div class="tts-slider-marks">
                <span>静音</span><span>适中</span><span>最大</span>
              </div>
            </div>

            <!-- 试听区 -->
            <div class="tts-preview">
              <div class="tts-preview-left">
                <span class="tts-preview-label">声音试听</span>
                <span class="muted" style="font-size: 11px;">「{{ PREVIEW_TEXT }}」</span>
              </div>
              <div class="tts-preview-right">
                <button
                  v-if="!previewing"
                  class="btn btn-secondary tts-preview-btn"
                  @click="previewVoice"
                >试听音色</button>
                <button
                  v-else
                  class="btn btn-danger tts-preview-btn"
                  @click="stopPreview"
                >停止播放</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 连通测试结果 -->
        <div v-if="testResult" class="test-result" :class="testResult.success ? 'test-ok' : 'test-fail'">
          <div class="test-head">
            <span class="badge" :class="testResult.success ? 'badge-success' : 'badge-danger'">
              {{ testResult.success ? '连通成功' : '连通失败' }}
            </span>
            <span v-if="testResult.success" class="muted" style="font-size: 11px;">
              耗时 {{ testResult.latencyMs }}ms
            </span>
          </div>
          <div class="test-msg">{{ testResult.message }}</div>
          <div v-if="testResult.success && testResult.reply" class="test-reply">
            AI 回复：{{ testResult.reply }}
          </div>
        </div>

        <div class="row" style="justify-content: flex-end; margin-top: 8px; gap: 8px;">
          <button class="btn btn-secondary" :disabled="testingForm" @click="testForm">
            {{ testingForm ? '测试中…' : '连通测试' }}
          </button>
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
  width: 600px;
  max-width: 100%;
  max-height: 90vh;
  overflow-y: auto;
}

small.muted {
  display: block;
  margin-top: 4px;
  font-size: 11px;
}

/* ===== TTS 配置区 ===== */
.tts-section {
  margin-top: 16px;
  padding: 16px;
  border-radius: var(--radius-md);
  background: var(--gradient-brand-soft);
  border: 1px solid var(--accent-border);
}

.tts-section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px dashed var(--accent-border);
}

.tts-section-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--accent);
}

.tts-section-hint {
  font-size: 11px;
  color: var(--text-tertiary);
}

.tts-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
  padding: 10px 12px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
}

.tts-preview-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.tts-preview-label {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-primary);
}

.tts-preview-right {
  flex-shrink: 0;
}

.tts-preview-btn {
  font-size: 12px;
  padding: 6px 14px;
}

/* 浏览器 TTS 滑块 */
.tts-slider {
  width: 100%;
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: var(--gradient-accent);
  border-radius: 2px;
  outline: none;
  cursor: pointer;
}
.tts-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid var(--accent-color, #6366f1);
  box-shadow: 0 1px 4px rgba(99, 102, 241, 0.35);
  cursor: pointer;
  transition: transform 0.15s ease;
}
.tts-slider::-webkit-slider-thumb:hover {
  transform: scale(1.15);
}
.tts-slider::-moz-range-thumb {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  border: 2px solid var(--accent-color, #6366f1);
  box-shadow: 0 1px 4px rgba(99, 102, 241, 0.35);
  cursor: pointer;
}
.tts-slider-marks {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 11px;
  color: var(--text-muted, #6b7280);
}

.test-result {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
  font-size: 12px;
}

.test-ok {
  background: var(--success-light);
  border-color: var(--success-border);
}

.test-fail {
  background: var(--danger-light);
  border-color: var(--danger-border);
}

.test-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.test-msg {
  color: var(--text-primary);
  word-break: break-word;
}

.test-reply {
  margin-top: 4px;
  color: var(--text-secondary);
  font-style: italic;
}

/* ===== 移动端响应式（安卓端适配） ===== */
@media (max-width: 768px) {
  /* 弹窗：占满更多视口宽度 */
  .modal {
    width: 94vw;
    max-height: 94dvh;
    padding: 16px;
  }
  .modal-mask {
    padding: 0;
  }

  /* 表格操作列按钮允许换行，避免横向溢出 */
  .table .row {
    flex-wrap: wrap;
    gap: 6px;
  }

  /* 弹窗底部按钮行允许换行 */
  .modal > .row {
    flex-wrap: wrap;
  }
}

@media (max-width: 480px) {
  /* 弹窗接近全屏 */
  .modal {
    width: 100vw;
    max-height: 100dvh;
    border-radius: 0;
    padding: 14px;
  }

  /* TTS 配置区内边距缩小 */
  .tts-section {
    padding: 12px;
    margin-top: 12px;
  }

  /* TTS 试听区：垂直堆叠，避免按钮溢出 */
  .tts-preview {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }
  .tts-preview-right {
    display: flex;
  }
  .tts-preview-btn {
    flex: 1;
  }

  /* TTS 区标题行允许换行 */
  .tts-section-head {
    flex-wrap: wrap;
    gap: 4px;
  }
}
</style>