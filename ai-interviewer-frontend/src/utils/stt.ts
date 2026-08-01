import { ref } from 'vue'

/**
 * 语音转文字工具：浏览器原生 SpeechRecognition API（Web Speech API）。
 *
 * 放弃 MediaRecorder 录音 + 后端 ASR 的方案（外部 API 易失效/密钥过期），
 * 改用浏览器原生 SpeechRecognition（Chrome / Edge 内置），实时识别无需后端参与。
 *
 * 交互流程：点击开始 → 实时语音识别 → 点击结束 → 返回识别文本
 */

// 浏览器原生 SpeechRecognition 构造函数（使用 any 避免 TS 类型报错）
const SpeechRecognitionCtor: any =
  typeof window !== 'undefined' &&
  ((window as any).SpeechRecognition || (window as any).webkitSpeechRecognition)

const isSupported = !!SpeechRecognitionCtor

// ===== 响应式状态 =====
const recording = ref(false)
/** 识别中 */
const recognizing = ref(false)
/** 识别结果文本 */
const finalText = ref('')
/** 错误信息 */
const errorMsg = ref('')

// ===== 内部状态 =====
let recognition: any = null
/** 累积的最终识别文本（每段 final result 追加） */
let accumulatedText = ''
/** 用来缓存 stopRecognition 的 resolve，以便在 result 或 end 事件中返回 */
let pendingResolve: ((text: string) => void) | null = null

// ===== 对外 API =====

/** 开始语音识别 */
export async function startRecognition(): Promise<void> {
  if (!isSupported) {
    errorMsg.value = '当前浏览器不支持语音识别，请使用 Chrome / Edge'
    return
  }
  if (recording.value) return

  errorMsg.value = ''
  finalText.value = ''
  accumulatedText = ''

  try {
    recognition = new SpeechRecognitionCtor()
    recognition.continuous = true
    recognition.interimResults = true
    recognition.lang = 'zh-CN'

    recognition.onstart = () => {
      recording.value = true
      recognizing.value = false
    }

    recognition.onresult = (event: any) => {
      // 取最后一段结果
      const last = event.results[event.results.length - 1]
      if (last.isFinal) {
        const text = last[0].transcript.trim()
        if (text) {
          accumulatedText += (accumulatedText ? '' : '') + text
        }
      }
    }

    recognition.onerror = (event: any) => {
      recording.value = false
      recognizing.value = false
      if (event.error === 'not-allowed') {
        errorMsg.value = '麦克风权限被拒绝，请在浏览器设置中允许'
      } else if (event.error === 'no-speech') {
        errorMsg.value = '未检测到语音'
      } else {
        errorMsg.value = '语音识别错误: ' + event.error
      }
      // 如果有等待的 resolve，用空字符串解决
      if (pendingResolve) {
        pendingResolve(accumulatedText)
        pendingResolve = null
      }
    }

    recognition.onend = () => {
      recording.value = false
      // 如果识别自然结束（非手动停止），用已累积的文本 resolve
      if (pendingResolve) {
        pendingResolve(accumulatedText)
        pendingResolve = null
      }
    }

    recognition.start()
  } catch (e: any) {
    errorMsg.value = '语音识别启动失败: ' + (e.message || e.name)
  }
}

/** 停止语音识别并返回最终识别文本 */
export async function stopRecognition(): Promise<string> {
  if (!recognition || !recording.value) {
    return finalText.value || accumulatedText
  }

  return new Promise<string>((resolve) => {
    pendingResolve = resolve
    try {
      recognition!.stop()
    } catch (e: any) {
      // stop 可能抛异常（如已结束），直接用已累积文本 resolve
      pendingResolve = null
      resolve(accumulatedText)
    }
  }).then((text) => {
    finalText.value = text
    recognizing.value = false
    recognition = null
    return text
  })
}

/** 中止语音识别（丢弃结果） */
export function abortRecognition(): void {
  if (recognition) {
    try {
      recognition.onend = null
      recognition.onerror = null
      recognition.onresult = null
      recognition.abort()
    } catch (e: any) {
      // 忽略 abort 异常
    }
    recognition = null
  }
  pendingResolve = null
  recording.value = false
  recognizing.value = false
  accumulatedText = ''
  finalText.value = ''
}

/** 重置文本状态（提交后调用） */
export function resetText(): void {
  finalText.value = ''
  accumulatedText = ''
}

/** 获取当前全部文本 */
export function getCurrentText(): string {
  return finalText.value || accumulatedText
}

export const sttSupported = isSupported
export const sttRecording = recording
export const sttRecognizing = recognizing
export const sttFinalText = finalText
export const sttError = errorMsg

// 兼容旧代码：interimText 不再使用，保留空 ref 避免 Interview.vue 报错
export const sttInterimText = ref('')