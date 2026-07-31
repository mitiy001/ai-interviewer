package com.aiinterviewer.service;

/**
 * TTS 语音合成 Service。
 * <p>
 * 调用已激活模型配置的 OpenAI 兼容 /audio/speech 接口，返回 MP3 音频。
 */
public interface TtsService {

    /**
     * 合成语音。
     *
     * @param text  要朗读的文本
     * @param voice 声音名称（alloy/echo/fable/onyx/nova/shimmer），null 用默认 alloy
     * @return MP3 音频字节
     */
    byte[] synthesize(String text, String voice);
}
