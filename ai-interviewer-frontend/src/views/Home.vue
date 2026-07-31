<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ModelConfigApi, QuestionBankApi, ResumeApi, SkillApi } from '@/api'
import type { ModelConfig, QuestionBank, Resume, Skill } from '@/api/types'

const resumes = ref<Resume[]>([])
const banks = ref<QuestionBank[]>([])
const skills = ref<Skill[]>([])
const models = ref<ModelConfig[]>([])
const loading = ref(false)
const errorMsg = ref('')

const activeModel = computed(() => models.value.find((m) => m.isActive === 1))
const activeSkill = computed(() => skills.value.find((s) => s.isActive === 1))
const readyToStart = computed(() => banks.value.length > 0 && !!activeModel.value && !!activeSkill.value)

async function loadStatus() {
  loading.value = true
  errorMsg.value = ''
  try {
    const [r, b, s, m] = await Promise.all([
      ResumeApi.list(),
      QuestionBankApi.list(),
      SkillApi.list(),
      ModelConfigApi.list(),
    ])
    resumes.value = r
    banks.value = b
    skills.value = s
    models.value = m
  } catch (e: any) {
    errorMsg.value = e.message || '加载状态失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadStatus)
</script>

<template>
  <div class="home">
    <!-- Hero -->
    <section class="hero fade-in-up">
      <div class="hero-bg"></div>
      <div class="hero-orb hero-orb-1"></div>
      <div class="hero-orb hero-orb-2"></div>
      <div class="hero-orb hero-orb-3"></div>
      <div class="hero-content">
        <span class="hero-badge">
          <span class="hero-badge-dot"></span>
          基于 Spring AI Alibaba Graph
        </span>
        <h1 class="hero-title">
          让 AI 成为你的<br />
          <span class="gradient-text">专属面试官</span>
        </h1>
        <p class="hero-desc">
          上传简历与题库，AI 模拟真实面试流程进行多轮提问与判定，生成评分报告与薪资报价。
          流式问答、语音播报、错题重练，全流程闭环。
        </p>
        <div class="hero-actions">
          <router-link
            to="/interview"
            class="btn btn-gradient"
            :class="{ 'btn-secondary': !readyToStart }"
          >
            {{ readyToStart ? '开始面试' : '前往面试' }}
            <span class="arrow">→</span>
          </router-link>
          <router-link to="/upload" class="btn btn-secondary">上传资料</router-link>
          <router-link to="/report" class="btn btn-secondary">查看报告</router-link>
        </div>
      </div>
    </section>

    <!-- 资料状态 -->
    <section class="card status-card fade-in-up" style="animation-delay: 0.08s">
      <div class="card-head">
        <h3 class="section-title" style="margin: 0;">面试前置资料状态</h3>
        <span v-if="readyToStart" class="badge badge-success">
          <span class="dot-ok"></span>就绪
        </span>
        <span v-else class="badge badge-warning">未就绪</span>
      </div>
      <div v-if="loading" class="empty">加载中…</div>
      <div v-else class="grid-status">
        <div class="status-cell">
          <div class="status-icon icon-blue">简</div>
          <div class="status-body">
            <div class="status-label">简历</div>
            <div v-if="resumes.length" class="badge badge-success">已上传 {{ resumes.length }} 份</div>
            <div v-else class="badge badge-warning">未上传（可选）</div>
            <div class="muted status-hint">可选，结合简历提问</div>
          </div>
        </div>
        <div class="status-cell">
          <div class="status-icon icon-violet">题</div>
          <div class="status-body">
            <div class="status-label">题库</div>
            <div v-if="banks.length" class="badge badge-success">已上传 {{ banks.length }} 个</div>
            <div v-else class="badge badge-danger">未上传</div>
            <div class="muted status-hint">必传，面试题来源</div>
          </div>
        </div>
        <div class="status-cell">
          <div class="status-icon icon-cyan">模</div>
          <div class="status-body">
            <div class="status-label">模型配置</div>
            <div v-if="activeModel" class="badge badge-success">已激活</div>
            <div v-else class="badge badge-danger">未激活</div>
            <div class="muted mono status-hint">
              {{ activeModel ? `${activeModel.name} / ${activeModel.model}` : '前往设置页激活' }}
            </div>
          </div>
        </div>
        <div class="status-cell">
          <div class="status-icon icon-amber">标</div>
          <div class="status-body">
            <div class="status-label">Skill 判定标准</div>
            <div v-if="activeSkill" class="badge badge-success">已激活</div>
            <div v-else class="badge badge-warning">未激活</div>
            <div class="muted status-hint">
              {{ activeSkill ? `${activeSkill.name} · ${activeSkill.position}` : '需初始化 seed.sql' }}
            </div>
          </div>
        </div>
      </div>
      <p v-if="errorMsg" class="error-text" style="margin-top: 14px;">{{ errorMsg }}</p>
      <p
        v-if="!loading && !readyToStart"
        class="error-text"
        style="margin-top: 14px;"
      >
        当前尚未满足开始面试的最低条件（题库 + 已激活模型 + 已激活 Skill）。
      </p>
    </section>

    <!-- 特性 -->
    <section class="features fade-in-up" style="animation-delay: 0.16s">
      <div class="feature-card">
        <div class="feature-icon icon-blue">
          <span>⚡</span>
        </div>
        <h4 class="feature-title">流式问答</h4>
        <p class="feature-desc">SSE 实时推送 AI 提问与判定，逐字浮现的对话体验，支持语音播报。</p>
      </div>
      <div class="feature-card">
        <div class="feature-icon icon-violet">
          <span>📊</span>
        </div>
        <h4 class="feature-title">结构化报告</h4>
        <p class="feature-desc">总分、优势不足、改进路径、薪资范围与模拟 Offer，一份报告完整呈现。</p>
      </div>
      <div class="feature-card">
        <div class="feature-icon icon-cyan">
          <span>🎯</span>
        </div>
        <h4 class="feature-title">错题重练</h4>
        <p class="feature-desc">基于面试表现自动生成单选、简答、代码题，针对性补强薄弱知识点。</p>
      </div>
    </section>

    <!-- 使用流程 -->
    <section class="card flow-card fade-in-up" style="animation-delay: 0.24s">
      <h3 class="section-title" style="margin: 0 0 16px;">使用流程</h3>
      <ol class="flow-list">
        <li>
          <span class="flow-num">1</span>
          <span>在「设置」页配置并激活一个 AI 模型（OpenAI 兼容接口）。</span>
        </li>
        <li>
          <span class="flow-num">2</span>
          <span>在「上传」页上传题库（必传）与简历（可选），系统将解析为面试题与候选人背景。</span>
        </li>
        <li>
          <span class="flow-num">3</span>
          <span>在「面试」页选择资料并开始面试，AI 通过 SSE 流式提问与判定。</span>
        </li>
        <li>
          <span class="flow-num">4</span>
          <span>面试结束后在「报告」页查看总分、各题评分与改进建议。</span>
        </li>
      </ol>
    </section>
  </div>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* ===== Hero ===== */
.hero {
  position: relative;
  border-radius: var(--radius-xl);
  overflow: hidden;
  padding: 48px 40px 44px;
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-lg);
}

.hero-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 15% 20%, rgba(99, 102, 241, 0.18), transparent 45%),
    radial-gradient(circle at 85% 30%, rgba(139, 92, 246, 0.16), transparent 45%),
    radial-gradient(circle at 50% 100%, rgba(6, 182, 212, 0.12), transparent 50%),
    linear-gradient(135deg, #ffffff 0%, #fafbff 100%);
  z-index: 0;
}

.hero-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(40px);
  opacity: 0.5;
  pointer-events: none;
  z-index: 0;
}

.hero-orb-1 {
  width: 180px;
  height: 180px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.45), transparent 70%);
  top: 10%;
  right: 8%;
  animation: drift 14s ease-in-out infinite;
}

.hero-orb-2 {
  width: 140px;
  height: 140px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.4), transparent 70%);
  bottom: 15%;
  left: 12%;
  animation: drift 18s ease-in-out infinite reverse;
}

.hero-orb-3 {
  width: 120px;
  height: 120px;
  background: radial-gradient(circle, rgba(6, 182, 212, 0.38), transparent 70%);
  top: 50%;
  right: 30%;
  animation: drift 16s ease-in-out infinite;
  animation-delay: -4s;
}

.hero-content {
  position: relative;
  z-index: 1;
  max-width: 640px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 5px 12px;
  background: rgba(99, 102, 241, 0.08);
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: var(--accent);
  margin-bottom: 18px;
}

.hero-badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.2);
  animation: pulse 2s ease-in-out infinite;
}

.hero-title {
  font-size: 38px;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: -0.5px;
  margin-bottom: 16px;
  color: var(--text-primary);
}

.hero-desc {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-secondary);
  margin-bottom: 26px;
  max-width: 560px;
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.hero-actions .arrow {
  transition: transform 0.18s var(--ease-out);
}

.hero-actions .btn:hover .arrow {
  transform: translateX(3px);
}

/* ===== 状态卡 ===== */
.status-card {
  padding: 22px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.dot-ok {
  display: inline-block;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--success);
  margin-right: 5px;
  box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.18);
}

.grid-status {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 14px;
}

.status-cell {
  display: flex;
  gap: 12px;
  padding: 14px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  transition: transform 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out),
              border-color 0.2s var(--ease-out);
}

.status-cell:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--accent-border);
}

.status-icon {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
}

.icon-blue {
  background: linear-gradient(135deg, #6366f1, #818cf8);
  box-shadow: 0 4px 10px -2px rgba(99, 102, 241, 0.4);
}
.icon-violet {
  background: linear-gradient(135deg, #8b5cf6, #a78bfa);
  box-shadow: 0 4px 10px -2px rgba(139, 92, 246, 0.4);
}
.icon-cyan {
  background: linear-gradient(135deg, #06b6d4, #22d3ee);
  box-shadow: 0 4px 10px -2px rgba(6, 182, 212, 0.4);
}
.icon-amber {
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
  box-shadow: 0 4px 10px -2px rgba(245, 158, 11, 0.4);
}

.status-body {
  flex: 1;
  min-width: 0;
}

.status-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
  font-weight: 600;
}

.status-hint {
  font-size: 11px;
  margin-top: 5px;
  line-height: 1.4;
}

/* ===== 特性卡 ===== */
.features {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 14px;
}

.feature-card {
  padding: 22px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  transition: transform 0.25s var(--ease-out), box-shadow 0.25s var(--ease-out),
              border-color 0.25s var(--ease-out);
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
  border-color: var(--accent-border);
}

.feature-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  margin-bottom: 14px;
}

.feature-title {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 6px;
  color: var(--text-primary);
}

.feature-desc {
  font-size: 12.5px;
  line-height: 1.65;
  color: var(--text-secondary);
}

/* ===== 流程 ===== */
.flow-card {
  padding: 22px;
}

.flow-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.flow-list li {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  font-size: 13px;
  color: var(--text-primary);
  transition: transform 0.2s var(--ease-out), border-color 0.2s var(--ease-out);
}

.flow-list li:hover {
  transform: translateX(4px);
  border-color: var(--accent-border);
}

.flow-num {
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--gradient-accent);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 3px 8px -2px rgba(99, 102, 241, 0.45);
}

/* ===== 移动端响应式（安卓端适配） ===== */
@media (max-width: 768px) {
  .hero {
    padding: 32px 22px 30px;
  }
  .hero-title {
    font-size: 28px;
    margin-bottom: 12px;
  }
  .hero-desc {
    font-size: 13px;
    margin-bottom: 20px;
  }
  .hero-badge {
    margin-bottom: 14px;
  }
  .hero-orb-1 { width: 120px; height: 120px; }
  .hero-orb-2 { width: 100px; height: 100px; }
  .hero-orb-3 { width: 80px; height: 80px; }
  .grid-status {
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 10px;
  }
  .features {
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 10px;
  }
  .status-card,
  .flow-card {
    padding: 16px;
  }
  .feature-card {
    padding: 16px;
  }
  .status-cell {
    padding: 12px;
    gap: 10px;
  }
}

@media (max-width: 480px) {
  .hero {
    padding: 24px 16px 24px;
    border-radius: var(--radius-lg);
  }
  .hero-title {
    font-size: 24px;
    line-height: 1.25;
  }
  .hero-desc {
    font-size: 12.5px;
    line-height: 1.6;
  }
  .grid-status {
    grid-template-columns: 1fr;
  }
  .features {
    grid-template-columns: 1fr;
  }
  .status-icon {
    width: 34px;
    height: 34px;
    font-size: 13px;
    border-radius: 8px;
  }
  .feature-icon {
    width: 38px;
    height: 38px;
    font-size: 18px;
    border-radius: 10px;
    margin-bottom: 10px;
  }
  .flow-list li {
    padding: 10px 12px;
    gap: 10px;
    font-size: 12.5px;
  }
  .flow-num {
    width: 24px;
    height: 24px;
    font-size: 11px;
  }
  .hero-actions {
    gap: 8px;
  }
  .hero-actions .btn {
    flex: 1;
    min-width: 0;
  }
}
</style>