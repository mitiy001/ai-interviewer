package com.aiinterviewer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型配置响应（脱敏：apiKey 不回显原文）
 */
@Data
public class ModelConfigResp {

    private Long id;
    private Long userId;
    private String name;
    private String provider;
    private String model;
    private String endpoint;
    private String judgeModel;
    private String judgeEndpoint;
    /** TTS 服务端点 */
    private String ttsEndpoint;
    /** TTS 模型名 */
    private String ttsModel;
    /** TTS 音色 */
    private String ttsVoice;
    /** 脱敏后的 tts_api_key：只保留前 4 + 后 4，中间打码 */
    private String ttsApiKeyMasked;
    /** 脱敏后的 api_key：只保留前 4 + 后 4，中间打码 */
    private String apiKeyMasked;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
