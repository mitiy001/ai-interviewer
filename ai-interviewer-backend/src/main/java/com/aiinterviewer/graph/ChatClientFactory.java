package com.aiinterviewer.graph;

import com.aiinterviewer.entity.ModelConfig;
import com.aiinterviewer.exception.BusinessException;
import com.aiinterviewer.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * ChatClient 工厂：根据 model_config 动态构造 OpenAI 兼容的 ChatModel / ChatClient。
 * <p>
 * 不依赖 Spring AI 的 starter 自动配置，每次都按 ModelConfig 实时构造，
 * 这样用户切换模型配置后立即生效。
 */
@Slf4j
@Component
public class ChatClientFactory {

    /**
     * 构造 ChatModel（OpenAI 兼容）
     *
     * @param config 模型配置（含 endpoint/apiKey/model）
     * @return ChatModel 实例
     */
    public ChatModel createChatModel(ModelConfig config) {
        validate(config);
        try {
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(config.getEndpoint())
                    .apiKey(config.getApiKey())
                    .readTimeout(Duration.ofSeconds(60))
                    .build();
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(config.getModel())
                    .temperature(0.7)
                    .build();
            return OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(options)
                    .build();
        } catch (Exception e) {
            log.error("构造 ChatModel 失败 configId={} model={}", config.getId(), config.getModel(), e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "模型构造失败: " + e.getMessage());
        }
    }

    /**
     * 构造 ChatClient（基于 ChatModel，支持 prompt 模板）
     */
    public ChatClient createChatClient(ModelConfig config) {
        return ChatClient.builder(createChatModel(config)).build();
    }

    /**
     * 构造判定专用的 ChatModel（若 judgeModel/judgeEndpoint 配了则用独立的，否则复用主模型）
     */
    public ChatModel createJudgeChatModel(ModelConfig config) {
        validate(config);
        // 若未配置独立判定模型，复用主模型
        if (config.getJudgeModel() == null || config.getJudgeModel().isBlank()
                || config.getJudgeEndpoint() == null || config.getJudgeEndpoint().isBlank()) {
            return createChatModel(config);
        }
        try {
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(config.getJudgeEndpoint())
                    .apiKey(config.getApiKey())
                    .readTimeout(Duration.ofSeconds(60))
                    .build();
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(config.getJudgeModel())
                    .temperature(0.3)
                    .build();
            return OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(options)
                    .build();
        } catch (Exception e) {
            log.error("构造 Judge ChatModel 失败 configId={} judgeModel={}",
                    config.getId(), config.getJudgeModel(), e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "判定模型构造失败: " + e.getMessage());
        }
    }

    private void validate(ModelConfig config) {
        if (config == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "模型配置为空");
        }
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "api_key 未配置");
        }
        if (config.getModel() == null || config.getModel().isBlank()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "model 未配置");
        }
        if (config.getEndpoint() == null || config.getEndpoint().isBlank()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "endpoint 未配置");
        }
    }
}
