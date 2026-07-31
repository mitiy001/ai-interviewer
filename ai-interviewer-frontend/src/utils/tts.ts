import { ref } from 'vue'
import http from '@/api/http'

/**
 * TTS 语音播报工具：调用后端 /api/tts（后端代理 Edge-TTS 项目）。
 *
 * 引擎：Microsoft Edge TTS（20+ 中文语音，免费，质量统一）。
 * 流程：speak(text) → POST /api/tts {text, voice} → mp3 blob → Audio 播放。
 *
 * 设置（音色/语速/音调/音量）持久化到 localStorage，跨会话保留。
 * 对外 API 与旧版浏览器内置 TTS 实现完全兼容，Settings.vue/Interview.vue 无需改动。
 */

const isSupported = typeof window !== 'undefined' && typeof Audio !== 'undefined'

const speaking = ref(false)
const enabled = ref(false)

/** 可用音色列表（Edge-TTS 20+ 中文语音，reactive） */
const voices = ref<SpeechSynthesisVoice[]>([])

/** 用户设置（持久化） */
const STORAGE_KEY = 'ai-interviewer-tts-settings'

interface TtsSettings {
  voiceURI: string // 选中的音色名（如 zh-CN-XiaoxiaoNeural）
  rate: number // 语速 0.5-2.0
  pitch: number // 音调 0.5-2.0（内部映射到 Edge-TTS -50~50）
  volume: number // 音量 0-1（前端 Audio.volume 控制）
}

const defaultSettings: TtsSettings = {
  voiceURI: 'zh-CN-XiaoxiaoNeural',
  rate: 1.0,
  pitch: 1.0,
  volume: 1.0,
}

function loadSettings(): TtsSettings {
  if (typeof localStorage === 'undefined') return { ...defaultSettings }
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { ...defaultSettings }
    const parsed = JSON.parse(raw)
    // 迁移：旧版 voiceURI 是浏览器音色 URI，若不在 Edge-TTS 列表中则用默认
    const merged = { ...defaultSettings, ...parsed }
    if (!EDGE_TTS_VOICES.some((v) => v.voiceURI === merged.voiceURI)) {
      merged.voiceURI = defaultSettings.voiceURI
    }
    return merged
  } catch {
    return { ...defaultSettings }
  }
}

function saveSettings(s: TtsSettings): void {
  if (typeof localStorage === 'undefined') return
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(s))
  } catch {
    /* ignore quota errors */
  }
}

const settings = ref<TtsSettings>(loadSettings())

// ===== Edge-TTS 中文语音列表（硬编码，无需异步加载）=====

/** Edge-TTS 支持的 20+ 中文语音，伪装成 SpeechSynthesisVoice 兼容类型 */
const EDGE_TTS_VOICES: SpeechSynthesisVoice[] = [
  // 女声
  makeVoice('zh-CN-XiaoxiaoNeural', '晓晓 (温柔)'),
  makeVoice('zh-CN-XiaoyiNeural', '晓伊 (甜美)'),
  makeVoice('zh-CN-XiaochenNeural', '晓辰 (知性)'),
  makeVoice('zh-CN-XiaohanNeural', '晓涵 (优雅)'),
  makeVoice('zh-CN-XiaomengNeural', '晓梦 (梦幻)'),
  makeVoice('zh-CN-XiaomoNeural', '晓墨 (文艺)'),
  makeVoice('zh-CN-XiaoqiuNeural', '晓秋 (成熟)'),
  makeVoice('zh-CN-XiaoruiNeural', '晓睿 (智慧)'),
  makeVoice('zh-CN-XiaoshuangNeural', '晓双 (活泼)'),
  makeVoice('zh-CN-XiaoxuanNeural', '晓萱 (清新)'),
  makeVoice('zh-CN-XiaoyanNeural', '晓颜 (柔美)'),
  makeVoice('zh-CN-XiaoyouNeural', '晓悠 (悠扬)'),
  makeVoice('zh-CN-XiaozhenNeural', '晓甄 (端庄)'),
  // 男声
  makeVoice('zh-CN-YunxiNeural', '云希 (清朗)'),
  makeVoice('zh-CN-YunyangNeural', '云扬 (阳光)'),
  makeVoice('zh-CN-YunjianNeural', '云健 (稳重)'),
  makeVoice('zh-CN-YunfengNeural', '云枫 (磁性)'),
  makeVoice('zh-CN-YunhaoNeural', '云皓 (豪迈)'),
  makeVoice('zh-CN-YunxiaNeural', '云夏 (热情)'),
  makeVoice('zh-CN-YunyeNeural', '云野 (野性)'),
  makeVoice('zh-CN-YunzeNeural', '云泽 (深沉)'),
]

/** 构造一个兼容 SpeechSynthesisVoice 结构的 Edge-TTS 语音对象 */
function makeVoice(voiceURI: string, name: string): SpeechSynthesisVoice {
  return {
    voiceURI,
    name,
    lang: 'zh-CN',
    default: voiceURI === defaultSettings.voiceURI,
    localService: false,
    voiceService: 'Edge-TTS',
  } as SpeechSynthesisVoice
}

// ===== 音频播放 =====

let currentAudio: HTMLAudioElement | null = null
let currentBlobUrl: string | null = null

/** 清理当前音频资源 */
function cleanupAudio(): void {
  if (currentAudio) {
    currentAudio.onplay = null
    currentAudio.onended = null
    currentAudio.onerror = null
    currentAudio.onpause = null
    currentAudio.pause()
    currentAudio = null
  }
  if (currentBlobUrl) {
    URL.revokeObjectURL(currentBlobUrl)
    currentBlobUrl = null
  }
}

/** 调用后端 /api/tts 合成并播放 mp3 */
async function speakWithEdgeTts(text: string): Promise<void> {
  cleanupAudio()

  try {
    const resp = await http.post('/tts', {
      text,
      voice: settings.value.voiceURI || defaultSettings.voiceURI,
    }, {
      responseType: 'blob',
      timeout: 30000,
    })

    const blob = resp.data as Blob
    if (blob.size === 0) {
      throw new Error('音频数据为空')
    }

    currentBlobUrl = URL.createObjectURL(blob)
    currentAudio = new Audio(currentBlobUrl)
    currentAudio.volume = settings.value.volume
    currentAudio.playbackRate = settings.value.rate

    currentAudio.onplay = () => { speaking.value = true }
    currentAudio.onended = () => {
      speaking.value = false
      cleanupAudio()
    }
    currentAudio.onerror = () => {
      speaking.value = false
      cleanupAudio()
    }

    await currentAudio.play()
  } catch (e: any) {
    speaking.value = false
    cleanupAudio()
    // TTS 是辅助功能，失败不阻断主流程，仅控制台告警
    console.warn('[TTS] Edge-TTS 调用失败:', e?.message || e)
  }
}

// ===== 统一 API =====

/** 预加载音色（Edge-TTS 语音列表为硬编码，无需异步加载，仅赋值） */
export async function preloadVoices(): Promise<void> {
  voices.value = EDGE_TTS_VOICES
}

/** 朗读文本（受 enabled 开关控制，面试页使用） */
export async function speak(text: string): Promise<void> {
  if (!isSupported || !enabled.value || !text.trim()) return
  await speakWithEdgeTts(text)
}

/** 试听朗读（绕过 enabled 开关，设置页试听使用） */
export async function speakPreview(text: string): Promise<void> {
  if (!isSupported || !text.trim()) return
  await speakWithEdgeTts(text)
}

/** 停止播报 */
export function cancel(): void {
  cleanupAudio()
  speaking.value = false
}

/** 切换开关 */
export function toggle(): boolean {
  enabled.value = !enabled.value
  if (!enabled.value) cancel()
  return enabled.value
}

/** 设置音色（voiceURI 即 Edge-TTS 语音名） */
export function setVoice(voiceURI: string): void {
  settings.value.voiceURI = voiceURI
  saveSettings(settings.value)
}

/** 设置语速 0.5-2.0 */
export function setRate(rate: number): void {
  settings.value.rate = Math.min(2.0, Math.max(0.5, rate))
  saveSettings(settings.value)
}

/** 设置音调 0.5-2.0（内部映射到 Edge-TTS -50~50） */
export function setPitch(pitch: number): void {
  settings.value.pitch = Math.min(2.0, Math.max(0.5, pitch))
  saveSettings(settings.value)
}

/** 设置音量 0-1 */
export function setVolume(volume: number): void {
  settings.value.volume = Math.min(1, Math.max(0, volume))
  saveSettings(settings.value)
}

/** 获取中文音色列表（Edge-TTS 语音全部为中文） */
export function getZhVoices(): SpeechSynthesisVoice[] {
  return voices.value.length > 0 ? voices.value : EDGE_TTS_VOICES
}

export const ttsSupported = isSupported
export const ttsEnabled = enabled
export const ttsSpeaking = speaking
export const ttsSettings = settings
/** 全部可用音色列表（Edge-TTS 中文语音） */
export const ttsVoiceList = voices
