<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { InterviewApi, ModelConfigApi, QuestionBankApi, ResumeApi, SkillApi } from '@/api'
import {
  ttsSupported,
  ttsEnabled,
  ttsSpeaking,
  speak as ttsSpeak,
  cancel as ttsCancel,
  toggle as ttsToggle,
  preloadVoices,
} from '@/utils/tts'
import {
  sttSupported,
  sttRecording,
  sttRecognizing,
  sttError,
  startRecognition,
  stopRecognition,
  abortRecognition,
  resetText,
} from '@/utils/stt'
import type {
  InterviewListItem,
  InterviewResumeResp,
  ModelConfig,
  QuestionBank,
  Resume,
  Skill,
} from '@/api/types'

const router = useRouter()

// ===== 列表 / 启动表单 =====
const list = ref<InterviewListItem[]>([])
const resumes = ref<Resume[]>([])
const banks = ref<QuestionBank[]>([])
const skills = ref<Skill[]>([])
const models = ref<ModelConfig[]>([])
const loadingList = ref(false)
const errorMsg = ref('')

const startForm = reactive({
  resumeId: undefined as number | undefined,
  bankId: undefined as number | undefined,
  skillId: undefined as number | undefined,
  maxTurns: 5,
  interviewType: 'TECH',
})

// ===== 会话状态 =====
type ChatRole = 'ai' | 'user' | 'system'
interface ChatMessage {
  role: ChatRole
  content: string
  turn?: number
  score?: number
  judgeReason?: string
  phase?: string
}

const interviewId = ref<number | null>(null)
const messages = ref<ChatMessage[]>([])
const phase = ref<string>('IDLE')
const waitingAnswer = ref(false)
const answerInput = ref('')
const submittingAnswer = ref(false)
const starting = ref(false)
const finished = ref(false)

// 断线重连状态
const reconnecting = ref(false)
const reconnectCount = ref(0)
const MAX_RECONNECT_ATTEMPTS = 5
let reconnectTimer: ReturnType<typeof setTimeout> | null = null

// 语音播报状态来自 @/utils/tts 模块
const totalScore = ref<number | null>(null)
const chatBodyRef = ref<HTMLElement | null>(null)
let eventSource: EventSource | null = null

const canSend = computed(() => waitingAnswer.value && answerInput.value.trim().length > 0 && !submittingAnswer.value)
const activeSkill = computed(() => skills.value.find((s) => s.isActive === 1))
const activeModel = computed(() => models.value.find((m) => m.isActive === 1))

/** 根据面试类型过滤可用的 Skill */
const filteredSkills = computed(() => {
  if (startForm.interviewType === 'HR') {
    // 人事面：显示 type 为 HR 的 skill
    return skills.value.filter(s => s.type === 'HR')
  }
  // 技术面：显示 type 为 TECH 或未设置 type 的 skill
  return skills.value.filter(s => !s.type || s.type === 'TECH')
})

async function loadList() {
  loadingList.value = true
  errorMsg.value = ''
  try {
    const [l, r, b, s, m] = await Promise.all([
      InterviewApi.list(),
      ResumeApi.list(),
      QuestionBankApi.list(),
      SkillApi.list(),
      ModelConfigApi.list(),
    ])
    list.value = l
    resumes.value = r
    banks.value = b
    skills.value = s
    models.value = m
    // 默认选中最新
    if (r.length) startForm.resumeId = r[0].id
    if (b.length) startForm.bankId = b[0].id
    if (s.length) {
      const act = s.find((x) => x.isActive === 1)
      startForm.skillId = act?.id ?? s[0].id
    }
  } catch (e: any) {
    const msg = e.message || '加载失败'
    errorMsg.value = msg.includes('系统繁忙') ? '服务器繁忙，请稍后刷新重试' : msg
  } finally {
    loadingList.value = false
  }
}

function preflightCheck(): string | null {
  if (banks.value.length === 0) return '请先在「上传」页上传题库后再开始面试'
  if (!activeModel.value) return '请先在「设置」页激活一个模型配置'
  if (!activeSkill.value) return '未发现激活的 Skill，请联系管理员初始化'
  return null
}

async function startInterview() {
  errorMsg.value = ''
  const err = preflightCheck()
  if (err) {
    errorMsg.value = err
    return
  }
  starting.value = true
  try {
    const resp = await InterviewApi.start({
      resumeId: startForm.resumeId,
      bankId: startForm.bankId,
      skillId: startForm.skillId,
      maxTurns: startForm.maxTurns,
      interviewType: startForm.interviewType,
    })
    interviewId.value = resp.interviewId
    messages.value = []
    finished.value = false
    totalScore.value = null
    connectSse(resp.interviewId)
  } catch (e: any) {
    errorMsg.value = e.message || '启动失败'
  } finally {
    starting.value = false
  }
}

function connectSse(id: number) {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  const url = InterviewApi.streamUrl(id)
  // withCredentials: true 确保跨域时携带 httpOnly Cookie（JWT 认证）
  const es = new EventSource(url, { withCredentials: true })
  eventSource = es

  es.addEventListener('phase', (e) => {
    const data = JSON.parse((e as MessageEvent).data)
    phase.value = data.phase
    if (data.phase === 'OPENING') {
      messages.value.push({ role: 'system', content: '面试开始，AI 正在生成开场白…' })
    } else if (data.phase === 'QUESTION') {
      waitingAnswer.value = false
    } else if (data.phase === 'JUDGE') {
      waitingAnswer.value = false
    } else if (data.phase === 'SUMMARY') {
      waitingAnswer.value = false
      messages.value.push({ role: 'system', content: '面试结束，正在生成总结报告…' })
    }
    scrollBottom()
  })

  es.addEventListener('ai', (e) => {
    const data = JSON.parse((e as MessageEvent).data)
    messages.value.push({
      role: 'ai',
      content: data.content,
      turn: data.turn,
    })
    ttsSpeak(data.content)
    scrollBottom()
  })

  es.addEventListener('wait_answer', (e) => {
    const data = JSON.parse((e as MessageEvent).data)
    waitingAnswer.value = true
    phase.value = 'WAIT_ANSWER'
    scrollBottom()
  })

  es.addEventListener('judge', (e) => {
    const data = JSON.parse((e as MessageEvent).data)
    // 找到最近一条 user 消息附加分数；同时单独推一条 system 评语
    const lastUser = [...messages.value].reverse().find((m) => m.role === 'user')
    if (lastUser) {
      lastUser.score = data.score
      lastUser.judgeReason = data.reason
    }
    messages.value.push({
      role: 'system',
      content: `第 ${data.turn + 1} 轮评分：${data.score} 分`,
      score: data.score,
    })
    scrollBottom()
  })

  es.addEventListener('done', (e) => {
    const data = JSON.parse((e as MessageEvent).data)
    finished.value = true
    totalScore.value = data.totalScore
    waitingAnswer.value = false
    messages.value.push({
      role: 'system',
      content: `面试结束，总分：${data.totalScore} 分`,
      score: data.totalScore,
    })
    scrollBottom()
    es.close()
    eventSource = null
    loadList()
  })

  es.addEventListener('error', (e) => {
    // EventSource 在连接关闭时也会触发 error，需要区分
    // 若已经 finished，则忽略
    if (finished.value) return
    const ev = e as MessageEvent
    const msg = ev.data
      ? (() => {
          try {
            return JSON.parse(ev.data).message
          } catch {
            return 'SSE 连接异常或中断'
          }
        })()
      : 'SSE 连接异常或中断'
    errorMsg.value = msg
    waitingAnswer.value = false
    // 主动关闭避免 EventSource 无限重连
    es.close()
    eventSource = null
    // 尝试自动重连
    attemptReconnect(interviewId.value)
  })
}

/**
 * 尝试断线重连：调用 resume 接口恢复状态，然后重新连接 SSE。
 * 使用指数退避策略，最多重试 MAX_RECONNECT_ATTEMPTS 次。
 */
async function attemptReconnect(id: number | null) {
  if (reconnecting.value || finished.value || id === null) return
  if (reconnectCount.value >= MAX_RECONNECT_ATTEMPTS) {
    errorMsg.value = `重连失败（已尝试 ${MAX_RECONNECT_ATTEMPTS} 次），请刷新页面后手动恢复面试`
    reconnecting.value = false
    return
  }

  reconnecting.value = true
  reconnectCount.value++
  const attempt = reconnectCount.value
  errorMsg.value = `检测到连接中断，正在尝试第 ${attempt} 次重连（共 ${MAX_RECONNECT_ATTEMPTS} 次）…`

  try {
    // 1. 调用 resume 接口获取历史状态
    const resumeData = await InterviewApi.resume(id)

    if (resumeData.status === 'FINISHED' || resumeData.status === 'ABORTED') {
      // 面试已结束，不再重连
      finished.value = true
      waitingAnswer.value = false
      reconnecting.value = false
      errorMsg.value = resumeData.status === 'FINISHED'
        ? '面试已结束'
        : '面试已中断'
      return
    }

    // 2. 恢复状态
    phase.value = resumeData.phase
    waitingAnswer.value = resumeData.waitingAnswer
    if (resumeData.currentQuestion) {
      answerInput.value = ''
    }

    // 3. 重建消息历史
    messages.value = resumeData.messages.map((m) => ({
      role: m.role as ChatRole,
      content: m.content,
      turn: m.turn,
      score: m.score,
      judgeReason: m.judgeReason,
    }))

    // 4. 清除错误提示，显示重连成功
    errorMsg.value = '重连成功，恢复面试中…'
    await nextTick()
    scrollBottom()

    // 5. 等待一小段时间后重新连接 SSE
    if (reconnectTimer) clearTimeout(reconnectTimer)
    reconnectTimer = setTimeout(() => {
      reconnecting.value = false
      errorMsg.value = ''
      connectSse(id)
    }, 500)
  } catch (e: any) {
    // 重连失败，按指数退避重试
    const delay = Math.min(1000 * Math.pow(2, attempt), 15000)
    errorMsg.value = `第 ${attempt} 次重连失败（${e.message || '未知错误'}），${Math.round(delay / 1000)} 秒后再次尝试…`
    if (reconnectTimer) clearTimeout(reconnectTimer)
    reconnectTimer = setTimeout(() => {
      reconnecting.value = false
      attemptReconnect(id)
    }, delay)
  }
}

async function sendAnswer() {
  if (!canSend.value || interviewId.value === null) return
  const text = answerInput.value.trim()
  submittingAnswer.value = true
  try {
    messages.value.push({ role: 'user', content: text })
    answerInput.value = ''
    waitingAnswer.value = false
    resetText()
    await InterviewApi.answer(interviewId.value, text)
    scrollBottom()
  } catch (e: any) {
    errorMsg.value = e.message || '提交回答失败'
    waitingAnswer.value = true
  } finally {
    submittingAnswer.value = false
  }
}

/** 切换麦克风录音：未录音时开始，录音中时停止并把结果填入输入框 */
async function toggleMic() {
  if (!sttSupported) {
    errorMsg.value = '当前浏览器不支持语音识别，请使用 Chrome / Edge'
    return
  }
  if (sttRecording.value) {
    // 停止录音并等待后端识别结果
    const text = await stopRecognition()
    if (text) answerInput.value = text
  } else {
    answerInput.value = ''
    await startRecognition()
  }
}

function scrollBottom() {
  nextTick(() => {
    const el = chatBodyRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

async function exitInterview() {
  const abortId = interviewId.value
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
  ttsCancel()
  // 通知后端中断面试，同步更新状态为 ABORTED，避免 RUNNING 残留导致无法删除
  if (abortId !== null && !finished.value) {
    try {
      await InterviewApi.abort(abortId)
    } catch (e: any) {
      // 中断失败不阻塞退出流程，后端删除接口仍会兜底处理
      console.warn('[Interview] 中断面试失败:', e?.message || e)
    }
  }
  interviewId.value = null
  messages.value = []
  phase.value = 'IDLE'
  waitingAnswer.value = false
  finished.value = false
  totalScore.value = null
  loadList()
}

function viewReport(id: number) {
  router.push({ path: '/report', query: { id: String(id) } })
}

function statusBadgeClass(s: string): string {
  if (s === 'FINISHED') return 'badge badge-success'
  if (s === 'RUNNING') return 'badge badge-info'
  if (s === 'ABORTED') return 'badge badge-danger'
  return 'badge badge-warning'
}

function interviewTypeLabel(type?: string): string {
  return type === 'HR' ? '人事面' : '技术面'
}

onMounted(async () => {
  await loadList()
  preloadVoices()
})
onUnmounted(() => {
  if (eventSource) eventSource.close()
  if (reconnectTimer) clearTimeout(reconnectTimer)
  ttsCancel()
  abortRecognition()
  // 路由切换时也通知后端中断（fire-and-forget，不阻塞卸载）
  const abortId = interviewId.value
  if (abortId !== null && !finished.value) {
    InterviewApi.abort(abortId).catch(() => { /* 忽略，删除接口会兜底 */ })
  }
})
</script>

<template>
  <div class="col">
    <!-- 断线重连提示 -->
    <p v-if="reconnecting" class="reconnect-banner">
      <span class="reconnect-icon">⟳</span>
      <span>{{ errorMsg }}</span>
    </p>
    <!-- 普通错误提示 -->
    <p v-else-if="errorMsg" class="error-text" style="background: #fef2f2; color: #dc2626; padding: 12px; border-radius: 8px; font-weight: bold; display: flex; justify-content: space-between; align-items: center;">
      <span>{{ errorMsg }}</span>
      <button class="btn btn-danger" @click="exitInterview" v-if="interviewId !== null" style="flex-shrink: 0;">
        退出面试
      </button>
    </p>

    <!-- 未开始面试：列表 + 启动表单 -->
    <template v-if="interviewId === null">
      <div class="card">
        <div class="row" style="justify-content: space-between;">
          <h2 class="section-title" style="margin: 0;">开始面试</h2>
          <button class="btn" :disabled="starting" @click="startInterview">
            {{ starting ? '启动中…' : '开始面试' }}
          </button>
        </div>
        <div class="row row-wrap" style="margin-top: 16px; gap: 16px;">
          <div class="form-group" style="flex: 1; min-width: 220px;">
            <label>简历（可选，不使用则仅依据题库面试）</label>
            <select v-model="startForm.resumeId" class="input">
              <option :value="undefined">不使用简历</option>
              <option v-for="r in resumes" :key="r.id" :value="r.id">{{ r.filename }}</option>
            </select>
          </div>
          <div class="form-group" style="flex: 1; min-width: 220px;">
            <label>题库（留空使用最新）</label>
            <select v-model="startForm.bankId" class="input">
              <option v-for="b in banks" :key="b.id" :value="b.id">{{ b.name }} ({{ b.questionCount }}题)</option>
            </select>
          </div>
          <div class="form-group" style="width: 160px;">
            <label>面试类型</label>
            <div class="row" style="gap: 8px; align-items: stretch;">
              <button
                class="btn"
                :class="startForm.interviewType === 'TECH' ? 'btn-gradient' : 'btn-secondary'"
                style="flex: 1; padding: 8px 12px;"
                @click="startForm.interviewType = 'TECH'; startForm.skillId = filteredSkills.length > 0 ? filteredSkills[0].id : undefined"
              >技术面</button>
              <button
                class="btn"
                :class="startForm.interviewType === 'HR' ? 'btn-gradient' : 'btn-secondary'"
                style="flex: 1; padding: 8px 12px;"
                @click="startForm.interviewType = 'HR'; startForm.skillId = filteredSkills.length > 0 ? filteredSkills[0].id : undefined"
              >人事面</button>
            </div>
          </div>
          <div class="form-group" style="flex: 1; min-width: 220px;">
            <label>{{ startForm.interviewType === 'HR' ? 'HR 面试官' : '面试等级 / Skill' }}</label>
            <select v-model="startForm.skillId" class="input">
              <option v-if="filteredSkills.length === 0" :value="undefined" disabled>
                {{ startForm.interviewType === 'HR' ? '暂无 HR 面试官，请先创建' : '暂无可用 Skill' }}
              </option>
              <option v-for="s in filteredSkills" :key="s.id" :value="s.id">
                {{ s.name }}{{ s.isActive === 1 ? '（已激活）' : '' }}
              </option>
            </select>
          </div>
          <div class="form-group" style="width: 120px;">
            <label>面试轮次</label>
            <input v-model.number="startForm.maxTurns" type="number" min="1" max="20" class="input" />
          </div>
        </div>
        <div class="row row-wrap" style="margin-top: 8px; gap: 12px; font-size: 12px;">
          <span class="muted">当前激活模型：</span>
          <span v-if="activeModel" class="mono">{{ activeModel.name }} / {{ activeModel.model }}</span>
          <span v-else class="error-text">未激活</span>
          <span class="muted" style="margin-left: 16px;">{{{
            startForm.interviewType === 'HR' ? 'HR 面试官：' : '激活 Skill：'
          }}</span>
          <span v-if="activeSkill" class="mono">{{ activeSkill.name }}</span>
          <span v-else class="error-text">未激活</span>
        </div>
      </div>

      <div class="card">
        <h3 class="section-title" style="margin: 0 0 12px;">历史记录</h3>
        <div v-if="loadingList" class="empty">加载中…</div>
        <div v-else-if="list.length === 0" class="empty">暂无面试记录</div>
        <div v-else class="history-table-wrap">
          <div class="table-wrap">
            <table class="table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>类型</th>
                  <th>状态</th>
                  <th>轮次</th>
                  <th>总分</th>
                  <th>开始时间</th>
                  <th>结束时间</th>
                  <th style="width: 100px;">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in list" :key="r.id">
                  <td>#{{ r.id }}</td>
                  <td><span class="badge">{{ interviewTypeLabel(r.interviewType) }}</span></td>
                  <td><span :class="statusBadgeClass(r.status)">{{ r.status }}</span></td>
                  <td>{{ r.maxTurns }}</td>
                  <td>{{ r.totalScore ?? '-' }}</td>
                  <td class="muted">{{ r.startTime }}</td>
                  <td class="muted">{{ r.endTime || '-' }}</td>
                  <td>
                    <button class="btn btn-secondary" @click="viewReport(r.id)">查看报告</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </template>

    <!-- 面试中：聊天界面 -->
    <template v-else>
      <div class="card chat-card">
        <div class="chat-header">
          <div class="chat-header-left">
            <div class="chat-avatar ai-avatar">AI</div>
            <div class="chat-header-info">
              <div class="chat-title">面试 #{{ interviewId }}</div>
              <div class="chat-sub">
                <span class="badge badge-info">{{ phase }}</span>
                <span v-if="finished" class="badge badge-success">总分 {{ totalScore }}</span>
                <span v-if="ttsSpeaking" class="tts-indicator">
                  <span class="tts-wave"></span>播报中
                </span>
              </div>
            </div>
          </div>
          <div class="row" style="gap: 8px;">
            <button
              v-if="ttsSupported"
              class="btn btn-secondary"
              :class="{ '