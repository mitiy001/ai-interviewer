<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { InterviewApi, ReportApi } from '@/api'
import type { InterviewListItem, Report } from '@/api/types'

const route = useRoute()
const router = useRouter()

const list = ref<InterviewListItem[]>([])
const report = ref<Report | null>(null)
const selectedId = ref<number | null>(null)
const loading = ref(false)
const errorMsg = ref('')
const deleting = ref(false)

const hasId = computed(() => selectedId.value !== null)

function statusBadgeClass(s: string): string {
  if (s === 'FINISHED') return 'badge badge-success'
  if (s === 'RUNNING') return 'badge badge-info'
  if (s === 'ABORTED') return 'badge badge-danger'
  return 'badge badge-warning'
}

async function loadList() {
  try {
    list.value = await InterviewApi.list()
  } catch (e: any) {
    errorMsg.value = e.message || '加载列表失败'
  }
}

async function loadReport(id: number) {
  loading.value = true
  errorMsg.value = ''
  report.value = null
  try {
    report.value = await ReportApi.get(id)
  } catch (e: any) {
    errorMsg.value = e.message || '加载报告失败'
  } finally {
    loading.value = false
  }
}

function pickFirst() {
  if (list.value.length > 0 && selectedId.value === null) {
    selectedId.value = list.value[0].id
    router.replace({ query: { id: String(selectedId.value) } })
    loadReport(selectedId.value)
  }
}

function selectRow(id: number) {
  selectedId.value = id
  router.replace({ query: { id: String(id) } })
  loadReport(id)
}

async function deleteInterview() {
  if (selectedId.value === null) return
  if (!confirm(`确认删除面试 #${selectedId.value} 及其报告和答题记录？此操作不可恢复。`)) return
  deleting.value = true
  errorMsg.value = ''
  try {
    await InterviewApi.delete(selectedId.value)
    selectedId.value = null
    report.value = null
    router.replace({ query: {} })
    await loadList()
    pickFirst()
  } catch (e: any) {
    errorMsg.value = e.message || '删除失败'
  } finally {
    deleting.value = false
  }
}

function gotoPractice() {
  if (selectedId.value === null) return
  router.push({ path: '/practice', query: { id: String(selectedId.value) } })
}

watch(
  () => route.query.id,
  (newId) => {
    if (newId && !Number.isNaN(Number(newId))) {
      const id = Number(newId)
      if (id !== selectedId.value) {
        selectedId.value = id
        loadReport(id)
      }
    }
  }
)

onMounted(async () => {
  await loadList()
  const qid = route.query.id
  if (qid && !Number.isNaN(Number(qid))) {
    selectedId.value = Number(qid)
    loadReport(Number(qid))
  } else {
    pickFirst()
  }
})
</script>

<template>
  <div class="report-layout">
    <p v-if="errorMsg" class="error-text">{{ errorMsg }}</p>

    <div class="card report-list">
      <h3 class="section-title" style="margin: 0 0 12px;">面试记录</h3>
      <div v-if="list.length === 0" class="empty">暂无记录</div>
      <div v-else class="list-items">
        <div
          v-for="(r, idx) in list"
          :key="r.id"
          class="list-item stagger"
          :class="{ active: selectedId === r.id }"
          :style="{ '--i': idx }"
          @click="selectRow(r.id)"
        >
          <div class="row" style="justify-content: space-between;">
            <span class="mono">#{{ r.id }}</span>
            <span :class="statusBadgeClass(r.status)">{{ r.status }}</span>
          </div>
          <div class="muted" style="font-size: 11px; margin-top: 4px;">
            {{ r.startTime }}
          </div>
          <div class="muted" style="font-size: 11px;">
            {{ r.totalScore !== null ? `总分 ${r.totalScore}` : '未评分' }} · {{ r.maxTurns }} 轮
          </div>
        </div>
      </div>
    </div>

    <div class="card report-detail">
      <div v-if="!hasId" class="empty">请从左侧选择一场面试查看报告</div>
      <div v-else-if="loading" class="empty">加载中…</div>
      <div v-else-if="!report" class="empty">暂无报告数据</div>
      <template v-else>
        <div class="report-head">
          <div class="report-head-left">
            <h2 class="report-title">面试 #{{ report.interviewId }} 报告</h2>
            <span :class="statusBadgeClass(report.status)" class="status-pill">{{ report.status }}</span>
          </div>
          <div class="row" style="gap: 8px;">
            <button class="btn btn-secondary" @click="gotoPractice">错题重练</button>
            <button class="btn btn-danger" :disabled="deleting" @click="deleteInterview">
              {{ deleting ? '删除中…' : '删除' }}
            </button>
          </div>
        </div>

        <div class="stat-row">
          <div class="stat">
            <div class="stat-icon stat-icon-score">分</div>
            <div class="stat-body">
              <div class="stat-label">总分</div>
              <div class="stat-value count-up">{{ report.totalScore ?? '-' }}</div>
            </div>
          </div>
          <div class="stat">
            <div class="stat-icon stat-icon-turns">轮</div>
            <div class="stat-body">
              <div class="stat-label">轮次</div>
              <div class="stat-value">{{ report.maxTurns }}</div>
            </div>
          </div>
          <div class="stat">
            <div class="stat-icon stat-icon-time">⏱</div>
            <div class="stat-body">
              <div class="stat-label">生成时间</div>
              <div class="stat-value small">{{ report.generatedAt || '-' }}</div>
            </div>
          </div>
        </div>

        <div v-if="report.salaryRange" class="report-block salary-card">
          <div class="salary-glow"></div>
          <div class="salary-card-inner">
            <div class="salary-card-left">
              <div class="salary-card-tag">薪资范围估算</div>
              <div class="salary-level">{{ report.salaryRange.level }}</div>
              <p v-if="report.salaryRange.note" class="salary-note">{{ report.salaryRange.note }}</p>
            </div>
            <div class="salary-numbers">
              <div class="salary-line">
                <span class="salary-label">月薪</span>
                <span class="salary-value">{{ report.salaryRange.monthlyMin }}-{{ report.salaryRange.monthlyMax }} {{ report.salaryRange.currency }}</span>
              </div>
              <div class="salary-divider"></div>
              <div class="salary-line">
                <span class="salary-label">年薪</span>
                <span class="salary-value salary-value-lg">{{ report.salaryRange.annualMin }}-{{ report.salaryRange.annualMax }} 万</span>
              </div>
            </div>
          </div>
        </div>

        <div v-if="report.salaryOffer" class="report-block offer-card">
          <div class="offer-glow"></div>
          <div class="offer-card-inner">
            <div class="offer-header">
              <div class="offer-header-left">
                <div class="offer-tag">模拟 Offer · 真实公司报价</div>
                <div class="offer-company">{{ report.salaryOffer.companyType }}</div>
              </div>
              <div class="offer-package">
                <span class="offer-package-label">年薪总包</span>
                <span class="offer-package-value" :class="{ 'no-offer': report.salaryOffer.annualPackage === 0 }">
                  {{ report.salaryOffer.annualPackage === 0 ? '未发 Offer' : `${report.salaryOffer.annualPackage} 万` }}
                </span>
              </div>
            </div>

            <div v-if="report.salaryOffer.annualPackage > 0" class="offer-grid">
              <div class="offer-item">
                <div class="offer-item-label">职级</div>
                <div class="offer-item-value offer-item-level">{{ report.salaryOffer.offerLevel || '-' }}</div>
              </div>
              <div class="offer-item">
                <div class="offer-item-label">月薪 Base</div>
                <div class="offer-item-value">{{ report.salaryOffer.monthlyBase }} {{ report.salaryOffer.currency }}</div>
              </div>
              <div class="offer-item">
                <div class="offer-item-label">月薪（含绩效）</div>
                <div class="offer-item-value">{{ report.salaryOffer.monthlyTotal }} {{ report.salaryOffer.currency }}</div>
              </div>
              <div class="offer-item">
                <div class="offer-item-label">年薪现金</div>
                <div class="offer-item-value">{{ report.salaryOffer.annualCash }} 万</div>
              </div>
              <div class="offer-item">
                <div class="offer-item-label">股票/期权/年</div>
              <div class="offer-item-value">{{ report.salaryOffer.annualEquity === 0 ? '无' : `${report.salaryOffer.annualEquity} 万` }}</div>
            </div>
            <div class="offer-item">
              <div class="offer-item-label">签字费</div>
              <div class="offer-item-value">{{ report.salaryOffer.signOnBonus === 0 ? '无' : `${report.salaryOffer.signOnBonus} 万` }}</div>
            </div>
          </div>

          <p v-if="report.salaryOffer.rationale" class="offer-rationale">{{ report.salaryOffer.rationale }}</p>
          </div>
        </div>

        <div v-if="report.overallComment" class="report-block">
          <h4 class="block-title">总评</h4>
          <p class="block-text">{{ report.overallComment }}</p>
        </div>
        <div v-else-if="report.summary" class="report-block">
          <h4 class="block-title">总结</h4>
          <p class="block-text">{{ report.summary }}</p>
        </div>

        <div v-if="report.strengths && report.strengths.length" class="report-block">
          <h4 class="block-title">优势</h4>
          <ul class="block-list">
            <li v-for="(item, i) in report.strengths" :key="`s${i}`" class="text-success">{{ item }}</li>
          </ul>
        </div>
        <div v-if="report.weaknesses && report.weaknesses.length" class="report-block">
          <h4 class="block-title">不足</h4>
          <ul class="block-list">
            <li v-for="(item, i) in report.weaknesses" :key="`w${i}`" class="text-danger">{{ item }}</li>
          </ul>
        </div>

        <div v-if="report.improvementDetails && report.improvementDetails.length" class="report-block">
          <h4 class="block-title">改进建议</h4>
          <div class="improvements">
            <div
              v-for="(imp, i) in report.improvementDetails"
              :key="i"
              class="improvement-card stagger"
              :style="{ '--i': i }"
            >
              <div class="imp-index">{{ i + 1 }}</div>
              <div class="imp-body">
                <div class="imp-field">
                  <span class="imp-label">问题</span>
                  <span>{{ imp.problem }}</span>
                </div>
                <div class="imp-field">
                  <span class="imp-label">学习路径</span>
                  <span>{{ imp.learningPath }}</span>
                </div>
                <div class="imp-field">
                  <span class="imp-label">练习建议</span>
                  <span>{{ imp.practice }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else-if="report.improvements && report.improvements.length" class="report-block">
          <h4 class="block-title">改进建议</h4>
          <ul class="block-list">
            <li v-for="(item, i) in report.improvements" :key="i">{{ item }}</li>
          </ul>
        </div>

        <div class="report-block">
          <h4 class="block-title">答题明细</h4>
          <div v-if="report.answers.length === 0" class="empty">暂无答题记录</div>
          <div v-else class="answers">
            <div
              v-for="(a, i) in report.answers"
              :key="a.id"
              class="answer-item stagger"
              :style="{ '--i': i }"
            >
              <div class="answer-head">
                <span class="badge badge-info">第 {{ a.turnIndex + 1 }} 轮</span>
                <span v-if="a.score !== null" class="badge badge-warning">{{ a.score }} 分</span>
                <span class="muted" style="font-size: 11px; margin-left: auto;">{{ a.answeredAt }}</span>
              </div>
              <div class="qa-block">
                <div class="qa-label">AI 提问</div>
                <div class="qa-content">{{ a.aiQuestion }}</div>
              </div>
              <div class="qa-block">
                <div class="qa-label">我的回答</div>
                <div class="qa-content">{{ a.userAnswer || '（未作答）' }}</div>
              </div>
              <div v-if="a.judgeReason" class="qa-block">
                <div class="qa-label">判定理由</div>
                <div class="qa-content muted">{{ a.judgeReason }}</div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.report-layout {
  display: grid;
  grid-template-columns: 270px 1fr;
  gap: 16px;
}

.report-list {
  padding: 16px;
  height: calc(100vh - 140px);
  overflow-y: auto;
}

.list-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.list-item {
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: transform 0.2s var(--ease-out), border-color 0.2s var(--ease-out),
              background 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out);
  background: var(--bg-secondary);
}

.list-item:hover {
  background: var(--bg-primary);
  transform: translateX(2px);
  border-color: var(--accent-border);
}

.list-item.active {
  border-color: var(--accent);
  background: var(--accent-light);
  box-shadow: var(--shadow-glow);
}

.report-detail {
  padding: 22px;
  height: calc(100vh - 140px);
  overflow-y: auto;
}

.report-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
  gap: 8px;
  flex-wrap: wrap;
}

.report-head-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.report-title {
  font-size: 20px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.2px;
}

.status-pill {
  font-size: 11px;
}

.stat-row {
  display: flex;
  gap: 14px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.stat {
  flex: 1;
  min-width: 140px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  transition: transform 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out);
}

.stat:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stat-icon {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 11px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: #fff;
}

.stat-icon-score {
  background: var(--gradient-accent);
  box-shadow: 0 4px 10px -2px rgba(99, 102, 241, 0.4);
}

.stat-icon-turns {
  background: linear-gradient(135deg, #06b6d4, #22d3ee);
  box-shadow: 0 4px 10px -2px rgba(6, 182, 212, 0.4);
}

.stat-icon-time {
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
  box-shadow: 0 4px 10px -2px rgba(245, 158, 11, 0.4);
  font-size: 17px;
}

.stat-body {
  flex: 1;
  min-width: 0;
}

.stat-label {
  color: var(--text-secondary);
  font-size: 11px;
  margin-bottom: 3px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.stat-value {
  font-size: 22px;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1.2;
}

.stat-value.small {
  font-size: 13px;
  font-weight: 600;
}

.report-block {
  margin-top: 20px;
}

.block-title {
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 10px;
  color: var(--text-primary);
  padding-bottom: 6px;
  border-bottom: 1px solid var(--border-color);
  letter-spacing: 0.2px;
}

.block-text {
  font-size: 13px;
  line-height: 1.75;
  color: var(--text-primary);
  white-space: pre-wrap;
}

.block-list {
  padding-left: 22px;
  font-size: 13px;
  line-height: 1.9;
}

.block-list li {
  margin-bottom: 4px;
}

.answers {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.answer-item {
  padding: 14px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  transition: border-color 0.2s var(--ease-out);
}

.answer-item:hover {
  border-color: var(--accent-border);
}

.answer-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.qa-block {
  margin-top: 8px;
}

.qa-label {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 3px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.qa-content {
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.salary-card {
  position: relative;
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: 1px solid var(--accent-border);
  background: var(--bg-secondary);
  box-shadow: var(--shadow-md);
}

.salary-glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 0% 0%, rgba(99, 102, 241, 0.14), transparent 50%),
    radial-gradient(circle at 100% 100%, rgba(6, 182, 212, 0.12), transparent 50%);
  pointer-events: none;
}

.salary-card-inner {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 20px;
  gap: 16px;
  flex-wrap: wrap;
}

.salary-card-left {
  flex: 1;
  min-width: 200px;
}

.salary-card-tag {
  display: inline-block;
  padding: 3px 10px;
  background: rgba(99, 102, 241, 0.1);
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
  border-radius: 999px;
  margin-bottom: 8px;
}

.salary-level {
  font-size: 22px;
  font-weight: 800;
  background: var(--gradient-accent);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: -0.3px;
}

.salary-note {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.salary-numbers {
  text-align: right;
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
}

.salary-line {
  display: flex;
  align-items: baseline;
  gap: 10px;
  justify-content: flex-end;
  margin-bottom: 6px;
}

.salary-divider {
  height: 1px;
  background: var(--border-color);
  margin: 6px 0;
}

.salary-label {
  font-size: 11px;
  color: var(--text-secondary);
  font-weight: 600;
}

.salary-value {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
}

.salary-value-lg {
  font-size: 17px;
  background: var(--gradient-accent);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.offer-card {
  position: relative;
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: 1px solid var(--success-border);
  background: var(--bg-secondary);
  box-shadow: var(--shadow-md);
}

.offer-glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 100% 0%, rgba(16, 185, 129, 0.14), transparent 50%),
    radial-gradient(circle at 0% 100%, rgba(139, 92, 246, 0.12), transparent 50%);
  pointer-events: none;
}

.offer-card-inner {
  position: relative;
  padding: 18px 20px;
}

.offer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  gap: 12px;
  flex-wrap: wrap;
}

.offer-header-left {
  flex: 1;
  min-width: 0;
}

.offer-tag {
  display: inline-block;
  padding: 3px 10px;
  background: var(--success-light);
  color: var(--success);
  font-size: 11px;
  font-weight: 600;
  border-radius: 999px;
  border: 1px solid var(--success-border);
  margin-bottom: 8px;
}

.offer-company {
  font-size: 17px;
  font-weight: 800;
  color: var(--text-primary);
  letter-spacing: -0.2px;
}

.offer-package {
  text-align: right;
  padding: 10px 18px;
  background: var(--success-light);
  border: 1px solid var(--success-border);
  border-radius: var(--radius-md);
}

.offer-package-label {
  display: block;
  font-size: 11px;
  color: var(--success);
  margin-bottom: 2px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.offer-package-value {
  font-size: 24px;
  font-weight: 800;
  background: var(--gradient-success);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: -0.3px;
}

.offer-package-value.no-offer {
  font-size: 16px;
  background: none;
  -webkit-text-fill-color: var(--text-secondary);
  color: var(--text-secondary);
}

.offer-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}

.offer-item {
  padding: 12px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  transition: transform 0.2s var(--ease-out), border-color 0.2s var(--ease-out);
}

.offer-item:hover {
  transform: translateY(-2px);
  border-color: var(--accent-border);
}

.offer-item-label {
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 4px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.offer-item-value {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
}

.offer-item-level {
  color: var(--accent);
}

.offer-rationale {
  margin-top: 10px;
  padding: 10px 12px;
  background: var(--bg-primary);
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-sm);
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
  font-style: italic;
}

.improvements {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.improvement-card {
  display: flex;
  gap: 14px;
  padding: 14px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  transition: transform 0.2s var(--ease-out), border-color 0.2s var(--ease-out);
}

.improvement-card:hover {
  transform: translateX(3px);
  border-color: var(--accent-border);
}

.imp-index {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--gradient-accent);
  color: var(--text-on-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 3px 8px -2px rgba(99, 102, 241, 0.45);
}

.imp-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.imp-field {
  font-size: 12.5px;
  line-height: 1.65;
  display: flex;
  gap: 10px;
}

.imp-label {
  flex-shrink: 0;
  display: inline-block;
  min-width: 64px;
  padding: 0 8px;
  background: var(--accent-light);
  border-radius: 999px;
  color: var(--accent);
  font-size: 11px;
  font-weight: 600;
  text-align: center;
  height: 22px;
  line-height: 22px;
}

.text-success {
  color: var(--success);
}

.text-danger {
  color: var(--danger);
}

@media (max-width: 768px) {
  .report-layout {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  .report-list {
    height: auto;
    max-height: 280px;
    padding: 12px;
  }
  .report-detail {
    height: auto;
    padding: 16px;
  }
  .report-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
}

@media (max-width: 480px) {
  .offer-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }
  .stat-label,
  .qa-label,
  .salary-card-tag,
  .salary-label,
  .offer-tag,
  .offer-package-label,
  .offer-item-label,
  .status-pill {
    font-size: 12px;
  }
  .improvement-card {
    flex-wrap: wrap;
  }
  .imp-field {
    flex-wrap: wrap;
  }
}
</style>