<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PracticeApi } from '@/api'
import type { PracticeQuestion } from '@/api/types'

const route = useRoute()
const router = useRouter()

const interviewId = ref<number | null>(null)
const questions = ref<PracticeQuestion[]>([])
const loading = ref(false)
const errorMsg = ref('')
const shortAnswerCount = ref(2)
const codeCount = ref(1)

// 每题作答状态
const userAnswers = ref<Record<number, string>>({})
const revealed = ref<Record<number, boolean>>({})

function typeLabel(t: string): string {
  if (t === 'single_choice') return '单选题'
  if (t === 'short_answer') return '简答题'
  if (t === 'code') return '代码题'
  return t
}

function typeBadgeClass(t: string): string {
  if (t === 'single_choice') return 'badge badge-info'
  if (t === 'short_answer') return 'badge badge-warning'
  if (t === 'code') return 'badge badge-success'
  return 'badge'
}

async function generate() {
  if (interviewId.value === null) return
  const sa = shortAnswerCount.value
  const cc = codeCount.value
  if (sa + cc <= 0) {
    errorMsg.value = '请至少选择 1 道题目'
    return
  }
  loading.value = true
  errorMsg.value = ''
  questions.value = []
  userAnswers.value = {}
  revealed.value = {}
  try {
    questions.value = await PracticeApi.generate(interviewId.value, sa, cc)
    if (questions.value.length === 0) {
      errorMsg.value = '未能生成练习题，请稍后重试'
    }
  } catch (e: any) {
    errorMsg.value = e.message || '生成练习题失败'
  } finally {
    loading.value = false
  }
}

function reveal(idx: number) {
  revealed.value[idx] = true
}

function isCorrect(idx: number, q: PracticeQuestion): boolean | null {
  if (!revealed.value[idx]) return null
  if (q.type !== 'single_choice') return null
  const user = userAnswers.value[idx]?.trim().toUpperCase()
  const ans = q.answer?.trim().toUpperCase()
  if (!user || !ans) return null
  return user === ans
}

onMounted(() => {
  const qid = route.query.id
  if (qid && !Number.isNaN(Number(qid))) {
    interviewId.value = Number(qid)
    generate()
  }
})
</script>

<template>
  <div class="card practice-page">
    <div class="row" style="justify-content: space-between; margin-bottom: 16px;">
      <h2 class="section-title" style="margin: 0;">
        错题重练
        <span v-if="interviewId" class="muted" style="font-size: 13px; margin-left: 8px;">
          基于面试 #{{ interviewId }}
        </span>
      </h2>
      <div class="row" style="gap: 8px;">
        <button class="btn btn-secondary" @click="router.push('/report')">返回报告</button>
      </div>
    </div>

    <!-- 题目类型与数量选择 -->
    <div class="practice-config">
      <div class="config-item">
        <label class="config-label">简答题</label>
        <div class="config-control">
          <button class="btn btn-icon" :disabled="shortAnswerCount <= 0" @click="shortAnswerCount--">−</button>
          <span class="config-num">{{ shortAnswerCount }}</span>
          <button class="btn btn-icon" :disabled="shortAnswerCount >= 5" @click="shortAnswerCount++">+</button>
        </div>
      </div>
      <div class="config-item">
        <label class="config-label">代码题</label>
        <div class="config-control">
          <button class="btn btn-icon" :disabled="codeCount <= 0" @click="codeCount--">−</button>
          <span class="config-num">{{ codeCount }}</span>
          <button class="btn btn-icon" :disabled="codeCount >= 5" @click="codeCount++">+</button>
        </div>
      </div>
      <div class="config-item config-action">
        <button class="btn btn-primary" :disabled="loading" @click="generate">
          {{ loading ? '生成中…' : shortAnswerCount + codeCount > 0 ? `生成 ${shortAnswerCount + codeCount} 道题` : '生成题目' }}
        </button>
      </div>
    </div>

    <p v-if="errorMsg" class="error-text">{{ errorMsg }}</p>

    <div v-if="loading" class="empty">正在根据错题生成练习题，请稍候…</div>

    <div v-else-if="questions.length > 0" class="questions">
      <div v-for="(q, idx) in questions" :key="idx" class="question-card">
        <div class="q-head">
          <span :class="typeBadgeClass(q.type)">{{ typeLabel(q.type) }}</span>
          <span class="muted" style="font-size: 11px;">知识点：{{ q.knowledgePoint }}</span>
        </div>

        <div class="q-title">{{ idx + 1 }}. {{ q.question }}</div>

        <!-- 单选 -->
        <div v-if="q.type === 'single_choice' && q.options" class="q-options">
          <label
            v-for="(opt, i) in q.options"
            :key="i"
            class="q-option"
            :class="{
              'opt-correct': revealed[idx] && String.fromCharCode(65 + i) === q.answer.toUpperCase(),
              'opt-wrong': revealed[idx] && userAnswers[idx] === String.fromCharCode(65 + i) && userAnswers[idx] !== q.answer.toUpperCase(),
            }"
          >
            <input
              type="radio"
              :name="`q${idx}`"
              :value="String.fromCharCode(65 + i)"
              v-model="userAnswers[idx]"
              :disabled="revealed[idx]"
            />
            <span class="opt-letter">{{ String.fromCharCode(65 + i) }}</span>
            <span class="opt-text">{{ opt }}</span>
          </label>
        </div>

        <!-- 简答 -->
        <div v-else-if="q.type === 'short_answer'" class="q-textarea">
          <textarea
            v-model="userAnswers[idx]"
            class="input"
            rows="4"
            placeholder="请输入你的回答…"
            :disabled="revealed[idx]"
          ></textarea>
        </div>

        <!-- 代码 -->
        <div v-else-if="q.type === 'code'" class="q-code">
          <textarea
            v-model="userAnswers[idx]"
            class="input code-input"
            rows="8"
            placeholder="请输入你的代码…"
            :disabled="revealed[idx]"
          ></textarea>
        </div>

        <!-- 操作按钮 -->
        <div class="row" style="margin-top: 10px; gap: 8px;">
          <button
            v-if="!revealed[idx]"
            class="btn btn-secondary"
            @click="reveal(idx)"
          >查看答案</button>
          <span v-if="isCorrect(idx, q) === true" class="badge badge-success">回答正确</span>
          <span v-if="isCorrect(idx, q) === false" class="badge badge-danger">回答错误</span>
        </div>

        <!-- 答案与解析 -->
        <div v-if="revealed[idx]" class="answer-block">
          <div class="qa-section">
            <div class="qa-label">参考答案</div>
            <div v-if="q.type === 'code'" class="qa-content code-block">{{ q.answer }}</div>
            <div v-else class="qa-content">{{ q.answer }}</div>
          </div>
          <div v-if="q.explanation" class="qa-section">
            <div class="qa-label">解析</div>
            <div class="qa-content muted">{{ q.explanation }}</div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="empty">请选择题目数量并点击「生成」创建练习题</div>
  </div>
</template>

<style scoped>
.practice-page {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.practice-config {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  margin-bottom: 16px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
}

.config-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
  white-space: nowrap;
}

.config-control {
  display: flex;
  align-items: center;
  gap: 4px;
}

.config-num {
  display: inline-flex;
  width: 32px;
  height: 28px;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-tertiary);
}

.config-action {
  margin-left: auto;
}

.btn-icon {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-icon:hover:not(:disabled) {
  background: var(--bg-hover);
  border-color: var(--accent);
}

.btn-icon:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.questions {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.question-card {
  padding: 16px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
}

.q-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.q-title {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.6;
  margin-bottom: 12px;
  white-space: pre-wrap;
}

.q-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.q-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 13px;
  transition: all 0.15s;
}

.q-option:hover {
  background: var(--bg-secondary);
}

.opt-correct {
  background: var(--success-light);
  border-color: var(--success);
}

.opt-wrong {
  background: var(--danger-light);
  border-color: var(--danger);
}

.opt-letter {
  display: inline-flex;
  width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--bg-primary);
  font-weight: 600;
  font-size: 11px;
}

.q-textarea textarea,
.q-code textarea {
  font-family: inherit;
}

.code-input {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
}

.answer-block {
  margin-top: 12px;
  padding: 12px;
  background: var(--bg-tertiary);
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
}

.qa-section {
  margin-bottom: 8px;
}

.qa-section:last-child {
  margin-bottom: 0;
}

.qa-label {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.qa-content {
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.code-block {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  background: var(--bg-primary);
  padding: 8px;
  border-radius: var(--radius-sm);
}
</stylign-items: center;\n  gap: 8px;\n  padding: 8px 10px;\n  background: var(--bg-tertiary);\n  border: 1px solid var(--border-color);\n  border-radius: var(--radius-sm);\n  cursor: pointer;\n  font-size: 13px;\n  transition: all 0.15s;\n}\n\n.q-option:hover {\n  background: var(--bg-secondary);\n}\n\n.opt-correct {\n  background: var(--success-light);\n  border-color: var(--success);\n}\n\n.opt-wrong {\n  background: var(--danger-light);\n  border-color: var(--danger);\n}\n\n.opt-letter {\n  display: inline-flex;\n  width: 20px;\n  height: 20px;\n  align-items: center;\n  justify-content: center;\n  border-radius: 50%;\n  background: var(--bg-primary);\n  font-weight: 600;\n  font-size: 11px;\n}\n\n.q-textarea textarea,\n.q-code textarea {\n  font-family: inherit;\n}\n\n.code-input {\n  font-family: 'Consolas', 'Monaco', monospace;\n  font-size: 12px;\n}\n\n.answer-block {\n  margin-top: 12px;\n  padding: 12px;\n  background: var(--bg-tertiary);\n  border-radius: var(--radius-sm);\n  border: 1px solid var(--border-color);\n}\n\n.qa-section {\n  margin-bottom: 8px;\n}\n\n.qa-section:last-child {\n  margin-bottom: 0;\n}\n\n.qa-label {\n  font-size: 11px;\n  color: var(--text-secondary);\n  margin-bottom: 4px;\n}\n\n.qa-content {\n  font-size: 13px;\n  line-height: 1.6;\n  white-space: pre-wrap;\n  word-break: break-word;\n}\n\n.code-block {\n  font-family: 'Consolas', 'Monaco', monospace;\n  font-size: 12px;\n  background: var(--bg-primary);\n  padding: 8px;\n  border-radius: var(--radius-sm);\n}\n</style>\n"}]