<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { QuestionBankApi, ResumeApi, SkillApi } from '@/api'
import type { QuestionBank, Resume, Skill } from '@/api/types'
import { parseResumeToSections, type ParsedResume } from '@/utils/resumeParser'

const resumes = ref<Resume[]>([])
const banks = ref<QuestionBank[]>([])
const skills = ref<Skill[]>([])
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const uploadingResume = ref(false)
const uploadingBank = ref(false)
const activatingSkill = ref(false)

const resumeInput = ref<HTMLInputElement | null>(null)
const bankInput = ref<HTMLInputElement | null>(null)

function clearMsg() {
  errorMsg.value = ''
  successMsg.value = ''
}

async function loadAll() {
  loading.value = true
  try {
    const [r, b, s] = await Promise.all([
      ResumeApi.list(),
      QuestionBankApi.list(),
      SkillApi.list(),
    ])
    resumes.value = r
    banks.value = b
    skills.value = s
  } catch (e: any) {
    errorMsg.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

async function onPickResume(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  clearMsg()
  uploadingResume.value = true
  try {
    const res = await ResumeApi.upload(file)
    successMsg.value = `简历「${file.name}」上传成功，解析字数 ${res.parsedLength ?? 0}`
    await loadAll()
  } catch (err: any) {
    errorMsg.value = err.message || '简历上传失败'
  } finally {
    uploadingResume.value = false
    target.value = ''
  }
}

async function onPickBank(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  clearMsg()
  uploadingBank.value = true
  try {
    const res = await QuestionBankApi.upload(file)
    successMsg.value = `题库「${file.name}」上传成功，解析题目 ${res.questionCount ?? 0} 条`
    await loadAll()
  } catch (err: any) {
    errorMsg.value = err.message || '题库上传失败'
  } finally {
    uploadingBank.value = false
    target.value = ''
  }
}

async function removeResume(id: number) {
  if (!confirm('确认删除该简历？')) return
  clearMsg()
  try {
    await ResumeApi.delete(id)
    successMsg.value = '简历已删除'
    await loadAll()
  } catch (e: any) {
    errorMsg.value = e.message || '删除失败'
  }
}

async function removeBank(id: number) {
  if (!confirm('确认删除该题库？')) return
  clearMsg()
  try {
    await QuestionBankApi.delete(id)
    successMsg.value = '题库已删除'
    await loadAll()
  } catch (e: any) {
    errorMsg.value = e.message || '删除失败'
  }
}

async function activateSkill(id: number) {
  clearMsg()
  activatingSkill.value = true
  try {
    await SkillApi.activate(id)
    await loadAll()
    successMsg.value = 'Skill 已激活'
  } catch (e: any) {
    errorMsg.value = e.message || '激活失败'
  } finally {
    activatingSkill.value = false
  }
}

function previewText(s: string | undefined, n = 100): string {
  if (!s) return ''
  return s.length > n ? s.slice(0, n) + '…' : s
}

// ===== 简历全文弹窗 =====
const resumeDialog = ref(false)
const resumeLoading = ref(false)
const resumeDetail = ref<Resume | null>(null)

/** 简历解析为结构化数据（按现实简历样式渲染） */
const parsedResume = computed<ParsedResume | null>(() => {
  if (!resumeDetail.value?.parsedText) return null
  return parseResumeToSections(resumeDetail.value.parsedText)
})

/** 联系方式图标映射 */
function contactIcon(icon: string): string {
  const map: Record<string, string> = { mail: '✉', phone: '☎', github: '⌥', location: '◉', clock: '◷' }
  return map[icon] || '·'
}

/** 判断是否核心技能（用于 solid 标签高亮） */
function isCoreSkill(tag: string): boolean {
  const core = ['Java', 'Spring', 'Spring Boot', 'Spring Cloud', 'Spring AI', 'MySQL', 'Redis', 'RabbitMQ', 'Linux', 'Python', 'Go', 'Vue', 'React']
  return core.some((c) => tag.toLowerCase() === c.toLowerCase())
}

async function viewResume(r: Resume) {
  resumeDialog.value = true
  resumeLoading.value = true
  resumeDetail.value = null
  try {
    resumeDetail.value = await ResumeApi.get(r.id)
  } catch (e: any) {
    errorMsg.value = e.message || '加载简历详情失败'
    resumeDialog.value = false
  } finally {
    resumeLoading.value = false
  }
}

function closeResumeDialog() {
  resumeDialog.value = false
  resumeDetail.value = null
}

onMounted(loadAll)
</script>

<template>
  <div class="col">
    <p v-if="errorMsg" class="error-text">{{ errorMsg }}</p>
    <p v-if="successMsg" class="success-text">{{ successMsg }}</p>

    <div v-if="loading" class="empty">加载中…</div>

    <!-- 资料校验概览 -->
    <div class="card">
      <h2 class="section-title" style="margin: 0 0 12px;">面试前置资料</h2>
      <div class="row row-wrap" style="gap: 16px;">
        <div class="status-item">
          <span class="muted">简历</span>
          <span v-if="resumes.length" class="badge badge-success">已上传 {{ resumes.length }}</span>
          <span v-else class="badge badge-warning">未上传（可选）</span>
        </div>
        <div class="status-item">
          <span class="muted">题库</span>
          <span v-if="banks.length" class="badge badge-success">已上传 {{ banks.length }}</span>
          <span v-else class="badge badge-danger">未上传</span>
        </div>
        <div class="status-item">
          <span class="muted">Skill 判定标准</span>
          <span v-if="skills.some((s) => s.isActive === 1)" class="badge badge-success">已激活</span>
          <span v-else class="badge badge-warning">未激活</span>
        </div>
      </div>
      <p class="muted" style="margin-top: 12px; font-size: 12px;">
        面试启动前会检查上述资料，题库必传，简历与 Skill 可选但建议配置。
      </p>
    </div>

    <!-- 简历管理 -->
    <div class="card">
      <div class="row" style="justify-content: space-between;">
        <h3 class="section-title" style="margin: 0;">简历</h3>
        <div class="row" style="gap: 8px;">
          <input
            ref="resumeInput"
            type="file"
            accept=".pdf,.doc,.docx,.md,.txt"
            style="display: none"
            @change="onPickResume"
          />
          <button class="btn" :disabled="uploadingResume" @click="resumeInput?.click()">
            {{ uploadingResume ? '上传中…' : '+ 上传简历' }}
          </button>
        </div>
      </div>
      <div v-if="resumes.length === 0" class="empty" style="margin-top: 12px;">
        暂无简历，支持 PDF / DOC / DOCX / MD / TXT
      </div>
      <div v-else class="table-wrap" style="margin-top: 12px;">
      <table class="table">
        <thead>
          <tr>
            <th>文件名</th>
            <th>字数</th>
            <th>内容预览</th>
            <th>上传时间</th>
            <th style="width: 150px;">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in resumes" :key="r.id">
            <td class="mono">{{ r.filename }}</td>
            <td>{{ r.parsedLength }}</td>
            <td class="muted">{{ previewText(r.parsedPreview) }}</td>
            <td class="muted">{{ r.uploadedAt }}</td>
            <td>
              <div class="row" style="gap: 6px;">
                <button class="btn btn-secondary" style="font-size: 12px; padding: 4px 10px;" @click="viewResume(r)">查看全文</button>
                <button class="btn btn-danger" style="font-size: 12px; padding: 4px 10px;" @click="removeResume(r.id)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
      </div>
    </div>

    <!-- 题库管理 -->
    <div class="card">
      <div class="row" style="justify-content: space-between;">
        <h3 class="section-title" style="margin: 0;">题库</h3>
        <div class="row" style="gap: 8px;">
          <input
            ref="bankInput"
            type="file"
            accept=".pdf,.doc,.docx,.md,.txt,.json"
            style="display: none"
            @change="onPickBank"
          />
          <button class="btn" :disabled="uploadingBank" @click="bankInput?.click()">
            {{ uploadingBank ? '上传中…' : '+ 上传题库' }}
          </button>
        </div>
      </div>
      <div v-if="banks.length === 0" class="empty" style="margin-top: 12px;">
        暂无题库，支持 PDF / DOC / DOCX / MD / TXT / JSON
      </div>
      <div v-else class="table-wrap" style="margin-top: 12px;">
      <table class="table">
        <thead>
          <tr>
            <th>名称</th>
            <th>来源</th>
            <th>题目数</th>
            <th>描述</th>
            <th>创建时间</th>
            <th style="width: 80px;">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="b in banks" :key="b.id">
            <td>{{ b.name }}</td>
            <td class="muted">{{ b.source }}</td>
            <td>{{ b.questionCount }}</td>
            <td class="muted">{{ previewText(b.description) }}</td>
            <td class="muted">{{ b.createdAt }}</td>
            <td>
              <button class="btn btn-danger" @click="removeBank(b.id)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      </div>
    </div>

    <!-- Skill 判定标准 -->
    <div class="card">
      <h3 class="section-title" style="margin: 0 0 12px;">Skill 判定标准</h3>
      <div v-if="skills.length === 0" class="empty">暂无 Skill，请通过数据库或后续管理页配置。</div>
      <div v-else class="table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>名称</th>
            <th>岗位</th>
            <th>评分维度</th>
            <th>状态</th>
            <th style="width: 100px;">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="s in skills" :key="s.id">
            <td>{{ s.name }}</td>
            <td class="muted">{{ s.position }}</td>
            <td class="muted">
              <span v-if="s.scoringDimensions && s.scoringDimensions.length">
                {{ s.scoringDimensions.map((d) => `${d.name}(${d.max})`).join(' / ') }}
              </span>
              <span v-else>-</span>
            </td>
            <td>
              <span v-if="s.isActive === 1" class="badge badge-success">已激活</span>
              <span v-else class="badge badge-warning">未激活</span>
            </td>
            <td>
              <button
                v-if="s.isActive !== 1"
                class="btn btn-secondary"
                :disabled="activatingSkill"
                @click="activateSkill(s.id)"
              >{{ activatingSkill ? '激活中…' : '激活' }}</button>
            </td>
          </tr>
        </tbody>
      </table>
      </div>
    </div>

    <!-- ===== 简历全文弹窗 ===== -->
    <div v-if="resumeDialog" class="resume-modal-mask" @click.self="closeResumeDialog">
      <div class="resume-modal">
        <div class="resume-modal-header">
          <div class="resume-modal-title">
            <span class="resume-modal-icon">📄</span>
            <span>{{ resumeDetail?.filename || '简历详情' }}</span>
          </div>
          <button class="resume-modal-close" @click="closeResumeDialog" aria-label="关闭">×</button>
        </div>
        <div class="resume-modal-body">
          <div v-if="resumeLoading" class="resume-loading">加载中…</div>
          <div v-else-if="parsedResume" class="resume-page">
            <aside class="resume-sidebar">
              <div class="resume-avatar">{{ parsedResume.name ? parsedResume.name.slice(0, 2) : '简历' }}</div>
              <div class="resume-name-block">
                <div class="resume-name">{{ parsedResume.name || '未识别' }}</div>
                <div v-if="parsedResume.jobTitle" class="resume-job-title">{{ parsedResume.jobTitle }}</div>
              </div>
              <section v-for="(sec, i) in parsedResume.sidebar" :key="`s${i}`" class="resume-side-section">
                <div class="resume-side-h">{{ sec.title }}</div>
                <ul v-if="sec.type === 'contact' && sec.contact" class="resume-contact-list">
                  <li v-for="(c, j) in sec.contact" :key="j">
                    <span class="resume-contact-ico">{{ contactIcon(c.icon) }}</span>
                    <span>{{ c.value }}</span>
                  </li>
                </ul>
                <div v-else-if="sec.type === 'education' && sec.education">
                  <div v-for="(edu, j) in sec.education" :key="j" class="resume-edu">
                    <div class="resume-edu-school">{{ edu.school }}</div>
                    <div v-if="edu.major" class="resume-edu-major">{{ edu.major }}</div>
                    <div v-if="edu.period" class="resume-edu-time">{{ edu.period }}</div>
                    <div v-if="edu.courses" class="resume-edu-courses">{{ edu.courses }}</div>
                  </div>
                </div>
                <div v-else-if="sec.type === 'skills' && sec.skills">
                  <div v-for="(g, j) in sec.skills" :key="j" class="resume-skill-group">
                    <div class="resume-skill-label">{{ g.label }}</div>
                    <div class="resume-tags">
                      <span v-for="(t, k) in g.tags" :key="k" class="resume-tag" :class="{ 'resume-tag-solid': isCoreSkill(t) }">{{ t }}</span>
                    </div>
                  </div>
                </div>
                <div v-else-if="sec.type === 'certs' && sec.certs">
                  <div v-for="(c, j) in sec.certs" :key="j" class="resume-cert-item">{{ c }}</div>
                </div>
                <div v-else-if="sec.type === 'campus' && sec.campus">
                  <div v-for="(c, j) in sec.campus" :key="j" class="resume-campus">
                    <div class="resume-campus-role">{{ c.role }}</div>
                    <div v-if="c.desc" class="resume-campus-desc">{{ c.desc }}</div>
                  </div>
                </div>
                <div v-else class="resume-side-text">
                  <p v-for="(p, j) in sec.paragraphs" :key="j">{{ p }}</p>
                </div>
              </section>
            </aside>
            <main class="resume-main">
              <section v-for="(sec, i) in parsedResume.main" :key="`m${i}`" class="resume-main-section">
                <div class="resume-main-h">{{ sec.title }}<span class="resume-main-en">{{ sec.en }}</span></div>
                <div v-if="sec.type === 'summary' && sec.summary" class="resume-summary-box">{{ sec.summary }}</div>
                <div v-else-if="sec.type === 'timeline' && sec.timeline">
                  <div v-for="(item, j) in sec.timeline" :key="j" class="resume-entry">
                    <div class="resume-entry-head">
                      <div>
                        <div class="resume-entry-title">{{ item.title }}</div>
                        <div v-if="item.subtitle" class="resume-entry-sub">{{ item.subtitle }}</div>
                      </div>
                      <div v-if="item.period" class="resume-entry-meta">{{ item.period }}</div>
                    </div>
                    <ul v-if="item.description.length" class="resume-entry-list">
                      <li v-for="(d, k) in item.description" :key="k">{{ d }}</li>
                    </ul>
                    <div v-if="item.metrics && item.metrics.length" class="resume-metrics">
                      <div v-for="(m, k) in item.metrics" :key="k" class="resume-metric">
                        <span class="resume-metric-num">{{ m.num }}</span>
                        <span class="resume-metric-lbl">{{ m.lbl }}</span>
                      </div>
                    </div>
                    <div v-if="item.techStack" class="resume-tech-row">技术栈：<span>{{ item.techStack }}</span></div>
                  </div>
                </div>
                <ul v-else-if="sec.type === 'eval' && sec.evalList" class="resume-eval-list">
                  <li v-for="(e, j) in sec.evalList" :key="j">{{ e }}</li>
                </ul>
              </section>
              <section v-if="parsedResume.main.length === 0" class="resume-main-section">
                <div class="resume-main-h">原始内容 <span class="resume-main-en">Raw</span></div>
                <pre class="resume-raw-text">{{ parsedResume.rawText }}</pre>
              </section>
            </main>
          </div>
          <div v-else class="resume-empty">简历内容为空</div>
        </div>
        <div class="resume-modal-footer">
          <span class="muted" style="font-size: 12px;">{{ resumeDetail?.parsedLength ?? 0 }} 字 · 上传于 {{ resumeDetail?.uploadedAt }}</span>
          <button class="btn btn-gradient" @click="closeResumeDialog">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.status-item { display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
.resume-modal-mask {
  position: fixed; inset: 0; background: rgba(15, 23, 42, 0.55); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center; z-index: 1000;
  animation: resume-fade-in 0.2s ease;
}
@keyframes resume-fade-in { from { opacity: 0; } to { opacity: 1; } }
.resume-modal {
  width: min(820px, 92vw); height: min(88vh, 900px); background: #f8fafc;
  border-radius: 10px; box-shadow: 0 20px 60px rgba(15, 23, 42, 0.3);
  display: flex; flex-direction: column; overflow: hidden;
  animation: resume-slide-up 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}
@keyframes resume-slide-up {
  from { opacity: 0; transform: translateY(24px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
.resume-modal-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px; background: #ffffff; border-bottom: 1px solid #e2e8f0; flex-shrink: 0;
}
.resume-modal-title { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; color: #0f172a; }
.resume-modal-icon { font-size: 18px; }
.resume-modal-close { width: 28px; height: 28px; border: none; background: transparent; font-size: 22px; line-height: 1; color: #64748b; cursor: pointer; border-radius: 6px; transition: all 0.15s ease; }
.resume-modal-close:hover { background: #f1f5f9; color: #0f172a; }
.resume-modal-body { flex: 1; overflow-y: auto; padding: 24px; background: #e2e8f0; }
.resume-loading, .resume-empty { text-align: center; color: #64748b; padding: 60px 0; font-size: 14px; }
.resume-page { max-width: 820px; margin: 0 auto; background: #fff; box-shadow: 0 4px 24px rgba(15,23,42,.08), 0 1px 3px rgba(15,23,42,.06); border-radius: 4px; overflow: hidden; display: grid; grid-template-columns: 264px 1fr; font-family: 'Noto Sans SC','PingFang SC','Microsoft YaHei',system-ui,sans-serif; color: #1f2937; font-size: 14.5px; line-height: 1.6; }
.resume-sidebar { background: #f8fafc; border-right: 1px solid #e5e7eb; padding: 36px 26px 32px; }
.resume-avatar { width: 92px; height: 92px; border-radius: 50%; background: linear-gradient(135deg, var(--accent,#2563eb), var(--accent-deep,#1e40af)); color: #fff; font-size: 34px; font-weight: 700; display: flex; align-items: center; justify-content: center; margin: 0 auto 18px; letter-spacing: 2px; box-shadow: 0 6px 18px rgba(37,99,235,.28); }
.resume-name-block { text-align: center; margin-bottom: 6px; }
.resume-name { font-size: 22px; font-weight: 700; color: #1f2937; letter-spacing: 1px; }
.resume-job-title { font-size: 13.5px; color: var(--accent,#2563eb); font-weight: 600; margin-top: 4px; letter-spacing: .5px; }
.resume-side-section { margin-top: 28px; }
.resume-side-h { font-size: 12px; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: 1.5px; padding-bottom: 8px; margin-bottom: 12px; border-bottom: 2px solid var(--accent,#2563eb); display: inline-block; }
.resume-contact-list { list-style: none; margin: 0; padding: 0; }
.resume-contact-list li { display: flex; align-items: center; gap: 9px; font-size: 13px; color: #4b5563; padding: 5px 0; word-break: break-all; }
.resume-contact-ico { width: 15px; flex: 0 0 15px; color: var(--accent,#2563eb); font-size: 13px; }
.resume-edu { margin-bottom: 12px; }
.resume-edu:last-child { margin-bottom: 0; }
.resume-edu-school { font-size: 14px; font-weight: 600; color: #1f2937; }
.resume-edu-major { font-size: 12.5px; color: #4b5563; margin-top: 2px; }
.resume-edu-time { font-size: 11.5px; color: #6b7280; margin-top: 2px; }
.resume-edu-courses { font-size: 12px; color: #6b7280; margin-top: 8px; line-height: 1.7; }
.resume-skill-group { margin-bottom: 12px; }
.resume-skill-group:last-child { margin-bottom: 0; }
.resume-skill-label { font-size: 12px; color: #4b5563; font-weight: 600; margin-bottom: 6px; }
.resume-tags { display: flex; flex-wrap: wrap; gap: 5px; }
.resume-tag { font-size: 11.5px; font-weight: 500; padding: 3px 9px; border-radius: 4px; background: #eef2ff; color: #1e40af; border: 1px solid #c7d2fe; line-height: 1.5; }
.resume-tag-solid { background: var(--accent,#2563eb); color: #fff; border-color: var(--accent,#2563eb); }
.resume-cert-item { font-size: 13px; color: #4b5563; padding-left: 12px; position: relative; line-height: 1.6; }
.resume-cert-item::before { content: ''; position: absolute; left: 0; top: 8px; width: 5px; height: 5px; border-radius: 50%; background: var(--accent,#2563eb); }
.resume-campus { margin-bottom: 10px; }
.resume-campus:last-child { margin-bottom: 0; }
.resume-campus-role { font-size: 13px; font-weight: 600; color: #1f2937; }
.resume-campus-desc { font-size: 12px; color: #6b7280; margin-top: 4px; line-height: 1.6; }
.resume-side-text { font-size: 12.5px; color: #4b5563; line-height: 1.7; }
.resume-side-text p { margin: 0 0 6px; }
.resume-main { padding: 36px 34px 32px; }
.resume-main-section { margin-bottom: 30px; }
.resume-main-section:last-child { margin-bottom: 0; }
.resume-main-h { font-size: 16px; font-weight: 700; color: #1f2937; display: flex; align-items: center; gap: 10px; margin-bottom: 18px; letter-spacing: .5px; }
.resume-main-h::before { content: ''; width: 4px; height: 18px; background: var(--accent,#2563eb); border-radius: 2px; }
.resume-main-en { font-size: 11px; font-weight: 500; color: #6b7280; letter-spacing: 1px; text-transform: uppercase; margin-left: auto; }
.resume-summary-box { background: #eff4ff; border-left: 3px solid var(--accent,#2563eb); padding: 14px 16px; border-radius: 0 6px 6px 0; font-size: 13px; color: #4b5563; line-height: 1.75; }
.resume-entry { position: relative; padding-left: 22px; padding-bottom: 22px; }
.resume-entry:last-child { padding-bottom: 0; }
.resume-entry::before { content: ''; position: absolute; left: 4px; top: 6px; width: 9px; height: 9px; border-radius: 50%; background: #fff; border: 2px solid var(--accent,#2563eb); z-index: 2; }
.resume-entry::after { content: ''; position: absolute; left: 8px; top: 14px; bottom: -4px; width: 1px; background: #e5e7eb; }
.resume-entry:last-child::after { display: none; }
.resume-entry-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 2px; }
.resume-entry-title { font-size: 14.5px; font-weight: 600; color: #1f2937; }
.resume-entry-sub { font-size: 12.5px; color: var(--accent,#2563eb); font-weight: 500; }
.resume-entry-meta { font-size: 11.5px; color: #6b7280; background: #f3f4f6; padding: 2px 8px; border-radius: 10px; white-space: nowrap; }
.resume-entry-list { list-style: none; margin: 6px 0 0; padding: 0; }
.resume-entry-list li { font-size: 13px; color: #4b5563; padding: 3px 0 3px 14px; position: relative; line-height: 1.7; }
.resume-entry-list li::before { content: ''; position: absolute; left: 2px; top: 11px; width: 4px; height: 4px; border-radius: 50%; background: #6b7280; }
.resume-metrics { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.resume-metric { background: #ecfdf5; border: 1px solid #a7f3d0; border-radius: 6px; padding: 6px 11px; display: flex; flex-direction: column; gap: 1px; }
.resume-metric-num { font-size: 16px; font-weight: 700; color: #047857; line-height: 1.1; }
.resume-metric-lbl { font-size: 10.5px; color: #059669; font-weight: 500; white-space: nowrap; }
.resume-tech-row { font-size: 11.5px; color: #6b7280; margin-top: 8px; padding-top: 8px; border-top: 1px dashed #e5e7eb; }
.resume-tech-row span { color: #4b5563; }
.resume-eval-list { list-style: none; margin: 0; padding: 0; }
.resume-eval-list li { font-size: 13px; color: #4b5563; padding: 4px 0 4px 16px; position: relative; line-height: 1.7; }
.resume-eval-list li::before { content: '▸'; position: absolute; left: 0; top: 4px; color: var(--accent,#2563eb); font-size: 12px; }
.resume-raw-text { font-family: 'PingFang SC','Microsoft YaHei',monospace; font-size: 13px; line-height: 1.8; color: #4b5563; white-space: pre-wrap; word-wrap: break-word; margin: 0; }
@media (max-width: 760px) {
  .resume-page { grid-template-columns: 1fr; }
  .resume-sidebar { border-right: none; border-bottom: 1px solid #e5e7eb; padding: 28px 22px 24px; }
  .resume-main { padding: 28px 22px; }
  .resume-entry-head { flex-direction: column; gap: 4px; }
}
.resume-modal-footer { display: flex; align-items: center; justify-content: space-between; padding: 12px 20px; background: #ffffff; border-top: 1px solid #e2e8f0; flex-shrink: 0; }
@media (max-width: 768px) {
  .resume-modal { width: 96vw; height: 92dvh; border-radius: 8px; }
  .resume-modal-header { padding: 12px 16px; }
  .resume-modal-title { font-size: 14px; gap: 6px; }
  .resume-modal-body { padding: 16px; }
  .resume-modal-footer { padding: 10px 16px; }
  .card .row { flex-wrap: wrap; gap: 10px; }
}
@media (max-width: 480px) {
  .resume-modal { width: 100vw; height: 100dvh; border-radius: 0; }
  .resume-modal-header { padding: 10px 14px; }
  .resume-modal-body { padding: 12px; }
  .resume-page { font-size: 13.5px; }
  .resume-sidebar { padding: 22px 18px 20px; }
  .resume-main { padding: 22px 18px; }
  .resume-avatar { width: 72px; height: 72px; font-size: 28px; margin-bottom: 14px; }
  .resume-name { font-size: 19px; }
  .resume-main-h { font-size: 15px; margin-bottom: 14px; }
  .resume-entry-title { font-size: 14px; }
  .row.row-wrap { flex-direction: column; gap: 10px; }
  .resume-modal-close { width: 36px; height: 36px; font-size: 24px; }
}
</style>