package com.aiinterviewer.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * TTS 语音合成请求。
 */
@Data
public class TtsReq {
    /** 要朗读的文本（最长 4096 字符，OpenAI TTS 限制） */
    @NotBlank
    private String text;
    /** 声音名称：alloy/echo/fable/onyx/nova/shimmer，默认 alloy */
    private String voice;
}
