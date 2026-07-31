package com.aiinterviewer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("model_config")
public class ModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String provider;
    private String apiKey;
    private String model;
    private String endpoint;
    private String judgeModel;
    private String judgeEndpoint;
    /** TTS 服务端点（Qwen3-TTS DashScope: https://dashscope.aliyuncs.com/api/v1） */
    private String ttsEndpoint;
    /** TTS 服务 API Key（为空则复用 api_key） */
    private String ttsApiKey;
    /** TTS 模型名（如 cosyvoice-v3-plus / qwen3-tts-vd-2026-01-26） */
    private String ttsModel;
    /** TTS 音色（如 Vivian / Serena / Uncle_Fu） */
    private String ttsVoice;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
