import http from './http'
import type {
  InterviewListItem,
  InterviewResumeResp,
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

function unwrap<T>(p: Promise<{ data: { data: T } }>): Promise<T> {
  return p.then((r) => r.data.data)
}

export const ModelConfigApi = {
  list: () => unwrap<ModelConfig[]>(http.get('/model-config')),
  get: (id: number) => unwrap<ModelConfig>(http.get(`/model-config/${id}`)),
  create: (req: ModelConfigReq) => unwrap<number>(http.post('/model-config', req)),
  update: (id: number, req: ModelConfigReq) =>
    http.put(`/model-config/${id}`, req).then((r) => r.data),
  activate: (id: number) =>
    http.post(`/model-config/${id}/activate`).then((r) => r.data),
  delete: (id: number) => http.delete(`/model-config/${id}`).then((r) => r.data),
  testSaved: (id: number) =>
    unwrap<ModelTestResult>(http.post(`/model-config/${id}/test`)),
  testUnsaved: (req: ModelConfigReq) =>
    unwrap<ModelTestResult>(http.post('/model-config/test', req)),
}

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

export const QuestionBankApi = {
  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return unwrap<UploadResult>(http.post('/question-bank/upload', form))
  },
  list: () => unwrap<QuestionBank[]>(http.get('/question-bank')),
  delete: (id: number) => http.delete(`/question-bank/${id}`).then((r) => r.data),
}

export const InterviewApi = {
  start: (req: StartReq) => unwrap<StartResp>(http.post('/interview/start', req)),
  list: () => unwrap<InterviewListItem[]>(http.get('/interview')),
  answer: (id: number, answer: string) =>
    http.post(`/interview/${id}/answer`, { answer }).then((r) => r.data),
  streamUrl: (id: number) => {
    const base = import.meta.env.VITE_API_BASE_URL || ''
    const token = localStorage.getItem('auth_token') || ''
    const qs = token ? `?token=${encodeURIComponent(token)}` : ''
    return `${base}/api/interview/${id}/stream${qs}`
  },
  resume: (id: number) => unwrap<InterviewResumeResp>(http.get(`/interview/${id}/resume`)),
  abort: (id: number) => http.post(`/interview/${id}/abort`).then((r) => r.data),
  delete: (id: number) => http.delete(`/interview/${id}`).then((r) => r.data),
}

export const ReportApi = {
  get: (id: number) => unwrap<Report>(http.get(`/interview/${id}/report`)),
}

export const PracticeApi = {
  generate: (interviewId: number, shortAnswerCount: number = 2, codeCount: number = 0) =>
    unwrap<PracticeQuestion[]>(http.post(`/interview/${interviewId}/practice`, { shortAnswerCount, codeCount }, { timeout: 120000 })),
}

export const TtsApi = {
  synthesize: (text: string, voice?: string) =>
    http
      .post('/tts', { text, voice }, { responseType: 'blob', timeout: 30000 })
      .then((r) => r.data as Blob),
}
