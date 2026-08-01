import http from './http'
import type {
  InterviewListItem,
  ModelConfig,
  ModelConfigReq,
  ModelTestResult,
  PracticeQuestion,
  QuestionBank,
  Report,
  Resume,
  Skill,
  StartReq,
  StartResp,
  UploadResult,
} from './types'

// 解包 Result<T> → T
function unwrap<T>(p: Promise<{ data: { data: T } }>): Promise<T> {
  return p.then((r) => r.data.data)
}

// ===== 模型配置 =====
export const ModelConfigApi = {
  list: () => unwrap<ModelConfig[]>(http.get('/model-config')),
  get: (id: number) => unwrap<ModelConfig>(http.get(`/model-config/${id}`)),
  create: (req: ModelConfigReq) => unwrap<number>(http.post('/model-config', req)),
  update: (id: number, req: ModelConfigReq) =>
    http.put(`/model-config/${id}`, req).then((r) => r.data),
  activate: (id: number) =>
    http.post(`/model-config/${id}/activate`).then((r) => r.data),
  delete: (id: number) => http.delete(`/model-config/${id}`).then((r) => r.data),
  /** 测试已保存配置连通性 */
  testSaved: (id: number) =>
    unwrap<ModelTestResult>(http.post(`/model-config/${id}/test`)),
  /** 测试未保存表单连通性 */
  testUnsaved: (req: ModelConfigReq) =>
    unwrap<ModelTestResult>(http.post('/model-config/test', req)),
}

// ===== Skill =====
export const SkillApi = {
  list: () => unwrap<Skill[]>(http.get('/skill')),
  get: (id: number) => unwrap<Skill>(http.get(`/skill/${id}`)),
  create: (req: { name: string; position: string; level: string; promptTemplate: string; scoringDimensions: { name: string; max: number }[] }) =>
    unwrap<number>(http.post('/skill', req)),
  update: (id: number, req: { name?: string; position?: string; level?: string; promptTemplate?: string; scoringDimensions?: { name: string; max: number }[] }) =>
    http.put(`/skill/${id}`, req).then((r) => r.data),
  delete: (id: number) => http.delete(`/skill/${id}`).then((r) => r.data),
  activate: (id: number) => http.post(`/skill/${id}/activate`).then((r) => r.data),
}

// ===== 简历 =====
export const ResumeApi = {
  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return unwrap<UploadResult>(http.post('/resume/upload', form))
  },
  list: () => unwrap<Resume[]>(http.get('/resume')),
  get: (id: number) => unwrap<Resume>(http.get(`/resume/${id}`)),
  delete: (id: number) => http.delete(`/resume/${id}`).then((r) => r.data),
}

// ===== 题库 =====
export const QuestionBankApi = {
  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return unwrap<UploadResult>(http.post('/question-bank/upload', form))
  },
  list: () => unwrap<QuestionBank[]>(http.get('/question-bank')),
  delete: (id: number) => http.delete(`/question-bank/${id}`).then((r) => r.data),
}

// ===== 面试 =====
export const InterviewApi = {
  start: (req: StartReq) => unwrap<StartResp>(http.post('/interview/start', req)),
  list: () => unwrap<InterviewListItem[]>(http.get('/interview')),
  answer: (id: number, answer: string) =>
    http.post(`/interview/${id}/answer`, { answer }).then((r) => r.data),
  /** SSE 流地址（EventSource 用），使用完整后端地址确保跨域连接正确 */
  streamUrl: (id: number) => {
    const base = import.meta.env.VITE_API_BASE_URL || ''
    return `${base}/api/interview/${id}/stream`
  },
  /** 中断面试（用户退出时调用，确保状态及时更新为 ABORTED） */
  abort: (id: number) => http.post(`/interview/${id}/abort`).then((r) => r.data),
  /** 删除面试记录及关联数据 */
  delete: (id: number) => http.delete(`/interview/${id}`).then((r) => r.data),
}

// ===== 报告 =====
export const ReportApi = {
  get: (id: number) => unwrap<Report>(http.get(`/interview/${id}/report`)),
}

// ===== 错题重练 =====
export const PracticeApi = {
  /** 根据面试中的错题生成练习题 */
  generate: (interviewId: number) =>
    unwrap<PracticeQuestion[]>(http.post(`/interview/${interviewId}/practice`)),
}

// ===== TTS 语音合成 =====
export const TtsApi = {
  /**
   * 合成语音：POST /api/tts { text, voice } → MP3 blob
   * 用于设置页声音试听与面试页语音播报。
   */
  synthesize: (text: string, voice?: string) =>
    http
      .post('/tts', { text, voice }, { responseType: 'blob', timeout: 30000 })
      .then((r) => r.data as Blob),
}