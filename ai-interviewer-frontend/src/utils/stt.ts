import { ref } from 'vue'
import http from '@/api/http'

/**
 * 语音转文字工具：浏览器 MediaRecorder 录音 + 后端 DashScope ASR 识别。
 *
 * 放弃浏览器原生 SpeechRecognition（Chromium 内核走 Google 服务器，国内被墙）。
 * 改用 MediaRecorder 录制音频 → 上传后端 /api/stt → 后端调用阿里云
 * DashScope SenseVoice/Paraformer 识别 → 返回文本。
 *
 * 交互流程：点击开始 → 持续录音 → 点击结束 → 上传识别 → 返回文本
 */

const isSupported =
  typeof window !== 'undefined' &&
  typeof navigator !== 'undefined' &&
  typeof navigator.mediaDevices !== 'undefined' &&
  typeof MediaRecorder !== 'undefined'

// ===== 响应式状态 =====
const recording = ref(false)
/** 识别中（上传后端并等待结果） */
const recognizing = ref(false)
/** 识别结果文本 */
const finalText = ref('')
/** 错误信息 */
const errorMsg = ref('')

// ===== 内部状态 =====
let mediaRecorder: MediaRecorder | null = null
let audioChunks: Blob[] = []
let mediaStream: MediaStream | null = null

/** 选择浏览器支持的 mimeType */
function pickMimeType(): string {
  const candidates = [
    'audio/webm;codecs=opus',
    'audio/webm',
    'audio/ogg;codecs=opus',
    'audio/mp4',
  ]
  for (const t of candidates) {
    if (MediaRecorder.isTypeSupported(t)) return t
  }
  return ''
}

// ===== 对外 API =====

/** 开始录音 */
export async function startRecognition(): Promise<void> {
  if (!isSupported) {
    errorMsg.value = '当前浏览器不支持录音，请使用 Chrome / Edge'
    return
  }
  if (recording.value) return
  errorMsg.value = ''
  finalText.value = ''
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const mimeType = pickMimeType()
    mediaRecorder = mimeType
      ? new MediaRecorder(mediaStream, { mimeType })
      : new MediaRecorder(mediaStream)
    audioChunks = []

    mediaRecorder.ondataavailable = (e: BlobEvent) => {
      if (e.data.size > 0) audioChunks.push(e.data)
    }

    mediaRecorder.start()
    recording.value = true
  } catch (e: any) {
    if (e.name === 'NotAllowedError') {
      errorMsg.value = '麦克风权限被拒绝，请在浏览器设置中允许'
    } else if (e.name === 'NotFoundError') {
      errorMsg.value = '未检测到麦克风设备'
    } else {
      errorMsg.value = '录音启动失败: ' + (e.message || e.name)
    }
  }
}

/** 停止录音并上传识别，返回识别文本 */
export async function stopRecognition(): Promise<string> {
  if (!mediaRecorder || !recording.value) return finalText.value

  return new Promise<string>((resolve) => {
    mediaRecorder!.onstop = async () => {
      recording.value = false
      // 释放麦克风
      if (mediaStream) {
        mediaStream.getTracks().forEach((t) => t.stop())
        mediaStream = null
      }

      if (audioChunks.length === 0) {
        errorMsg.value = '未录制到音频'
        resolve('')
        return
      }

      const mimeType = mediaRecorder?.mimeType || 'audio/webm'
      const blob = new Blob(audioChunks, { type: mimeType })
      const text = await uploadAndRecognize(blob)
      finalText.value = text
      resolve(text)
    }
    mediaRecorder!.stop()
  })
}

/** 中止录音（丢弃结果） */
export function abortRecognition(): void {
  if (mediaRecorder && recording.value) {
    mediaRecorder.onstop = null
    mediaRecorder.stop()
  }
  if (mediaStream) {
    mediaStream.getTracks().forEach((t) => t.stop())
    mediaStream = null
  }
  mediaRecorder = null
  audioChunks = []
  recording.value = false
  recognizing.value = false
  finalText.value = ''
}

/** 上传音频到后端识别 */
async function uploadAndRecognize(blob: Blob): Promise<string> {
  recognizing.value = true
  errorMsg.value = ''
  try {
    const form = new FormData()
    const ext = blob.type.includes('webm')
      ? 'webm'
      : blob.type.includes('ogg')
        ? 'ogg'
        : blob.type.includes('mp4')
          ? 'mp4'
          : 'wav'
    form.append('file', blob, `audio.${ext}`)

    const resp = await http.post('/stt', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 90000, // 识别可能较慢，放宽到 90s
    })
    // 后端返回 { text: "..." }
    const data = resp.data
    const text = typeof data === 'string' ? data : (data?.text || data?.data?.text || '')
    return (text || '').trim()
  } catch (e: any) {
    const msg = e?.response?.data?.message || e.message || '语音识别失败'
    errorMsg.value = msg
    return ''
  } finally {
    recognizing.value = false
  }
}

/** 重置状态（提交后调用） */
export function resetText(): void {
  finalText.value = ''
}

/** 获取当前全部文本 */
export function getCurrentText(): string {
  return finalText.value.trim()
}

export const sttSupported = isSupported
export const sttRecording = recording
export const sttRecognizing = recognizing
export const sttFinalText = finalText
export const sttError = errorMsg

// 兼容旧代码：interimText 不再使用，保留空 ref 避免 Interview.vue 报错
export const sttInterimText = ref('')
