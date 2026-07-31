// ===== 后端 Result<T> 包装 =====
export interface Result<T> {
  code: number
  message: string
  data: T
}

// ===== 模型配置 =====
export interface ModelConfig {
  id: number
  name: string
  provider: string
  apiKeyMasked: string
  model: string
  endpoint: string
  judgeModel?: string
  judgeEndpoint?: string
  /** TTS 服务端点（Qwen3-TTS DashScope） */
  ttsEndpoint?: string
  /** TTS 模型名 */
  ttsModel?: string
  /** TTS 音色 */
  ttsVoice?: string
  /** 脱敏后的 tts_api_key */
  ttsApiKeyMasked?: string
  isActive: number
  createdAt: string
  updatedAt: string
}

export interface ModelConfigReq {
  /** 编辑场景下传入，用于连通测试时回查数据库已保存的 apiKey；新建时留空 */
  id?: number
  name: string
  provider: string
  apiKey?: string
  model: string
  endpoint: string
  judgeModel?: string
  judgeEndpoint?: string
  /** TTS 服务端点 */
  ttsEndpoint?: string
  /** TTS 服务 API Key（为空则复用 api_key） */
  ttsApiKey?: string
  /** TTS 模型名 */
  ttsModel?: string
  /** TTS 音色 */
  ttsVoice?: string
  isActive?: number
}

// ===== 模型连通测试 =====
export interface ModelTestResult {
  success: boolean
  message: string
  reply: string
  latencyMs: number
}

// ===== Skill =====
export interface Skill {
  id: number
  name: string
  position: string
  /** 工程师等级：junior/mid/senior */
  level: string
  promptTemplate: string
  scoringDimensions: { name: string; max: number }[]
  isActive: number
}

// ===== 简历 / 题库 =====
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
  /** 解析后纯文本全文（仅详情接口返回） */
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

// ===== 面试 =====
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

// ===== 报告 =====
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

/** 薪资范围 */
export interface SalaryRange {
  level: string
  monthlyMin: number
  monthlyMax: number
  annualMin: number
  annualMax: number
  currency: string
  note: string
}

/** 薪资报价（模拟真实公司 offer） */
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

/** 结构化改进建议 */
export interface ImprovementDetail {
  problem: string
  learningPath: string
  practice: string
}

export interface Report {
  interviewId: number
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

/** 错题重练题目 */
export interface PracticeQuestion {
  type: 'single_choice' | 'short_answer' | 'code'
  question: string
  options?: string[]
  answer: string
  explanation: string
  knowledgePoint: string
}
