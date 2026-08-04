// ===== 后端 Result<T> 包装 =====
export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface ModelConfig {
  id: number
  name: string
  provider: string
  apiKeyMasked: string
  model: string
  endpoint: string
  judgeModel?: string
  judgeEndpoint?: string
  ttsEndpoint?: string
  ttsModel?: string
  ttsVoice?: string
  ttsApiKeyMasked?: string
  isActive: number
  createdAt: string
  updatedAt: string
}

export interface ModelConfigReq {
  id?: number
  name: string
  provider: string
  apiKey?: string
  model: string
  endpoint: string
  judgeModel?: string
  judgeEndpoint?: string
  ttsEndpoint?: string
  ttsApiKey?: string
  ttsModel?: string
  ttsVoice?: string
  isActive?: number
}

export interface ModelTestResult {
  success: boolean
  message: string
  reply: string
  latencyMs: number
}

export interface Skill {
  id: number
  name: string
  position: string
  level: string
  promptTemplate: string
  scoringDimensions: { name: string; max: number }[]
  isActive: number
}

export interface UploadResult {
  id: number
  questionCount?: number | null
  parsedLength?: number | null
}

export interface Resume {
  id: number
  userId: number
  filename: string
  parsedPreview: string
  parsedLength: number
  parsedText?: string
  uploadedAt: string
}

export interface QuestionBank {
  id: number
  userId: number
  name: string
  source: string
  description?: string
  questionCount: number
  createdAt: string
}

export interface StartReq {
  resumeId?: number
  bankId?: number
  modelConfigId?: number
  skillId?: number
  maxTurns?: number
}

export interface StartResp {
  interviewId: number
  message: string
}

export interface InterviewListItem {
  id: number
  status: string
  maxTurns: number
  totalScore: number | null
  startTime: string
  endTime: string | null
}

export interface AnswerItem {
  id: number
  turnIndex: number
  questionId: number
  aiQuestion: string
  userAnswer: string
  score: number | null
  judgeReason: string
  answeredAt: string
}

export interface SalaryRange {
  level: string
  monthlyMin: number
  monthlyMax: number
  annualMin: number
  annualMax: number
  currency: string
  note: string
}

export interface SalaryOffer {
  companyType: string
  offerLevel: string
  monthlyBase: number
  monthlyTotal: number
  annualCash: number
  annualEquity: number
  signOnBonus: number
  annualPackage: number
  currency: string
  rationale: string
}

export interface ImprovementDetail {
  problem: string
  learningPath: string
  practice: string
}

export interface Report {
  interviewId: number
  position?: string
  status: string
  totalScore: number | null
  maxTurns: number
  overallComment?: string
  strengths?: string[]
  weaknesses?: string[]
  improvements?: string[]
  improvementDetails?: ImprovementDetail[]
  salaryRange?: SalaryRange
  salaryOffer?: SalaryOffer
  summary?: string
  generatedAt?: string
  answers: AnswerItem[]
}

export interface ResumeMessageItem {
  role: string
  content: string
  turn?: number
  score?: number
  judgeReason?: string
}

export interface InterviewResumeResp {
  interviewId: number
  status: string
  phase: string
  turnIndex: number
  maxTurns: number
  waitingAnswer: boolean
  currentQuestion?: string
  messages: ResumeMessageItem[]
}

export interface PracticeQuestion {
  type: 'single_choice' | 'short_answer' | 'code'
  question: string
  options?: string[]
  answer: string
  explanation: string
  knowledgePoint: string
}
