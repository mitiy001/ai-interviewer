package com.aiinterviewer.service.impl;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.entity.ModelConfig;
import com.aiinterviewer.exception.BusinessException;
import com.aiinterviewer.service.ModelConfigService;
import com.aiinterviewer.service.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * TTS 语音合成 Service 实现：调用 Edge-TTS 项目（https://github.com/TT3301/Edge-TTS）。
 * <p>
 * 调用规范（兼容 OpenAI TTS API 格式）：
 *   POST {endpoint}/v1/audio/speech
 *   Content-Type: application/json
 *   Body: { "input": "要朗读的文本",
 *           "voice": "zh-CN-XiaoxiaoNeural",
 *           "speed": 1.0,
 *           "pitch": "0",
 *           "style": "general" }
 *   响应：直接返回音频二进制（mp3），无需二次下载
 * <p>
 * 引擎：
 *   - TTS: Microsoft Edge TTS（20+ 中文语音，免费）
 *   - 无需 API Key（公共服务零配置）
 * <p>
 * 端点可通过 ModelConfig.ttsEndpoint 配置（默认公共服务 https://tts.wangwangit.com），
 * 也可自部署到 Cloudflare Workers 后填入自定义域名。
 * voice 优先级：请求传入 > ModelConfig.ttsVoice > 默认 zh-CN-XiaoxiaoNeural。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsServiceImpl implements TtsService {

    private final ModelConfigService modelConfigService;
    private final RestTemplate restTemplate = new RestTemplate();

    /** Edge-TTS 默认公共服务端点（基于 Cloudflare Workers，全球边缘加速） */
    private static final String DEFAULT_TTS_ENDPOINT = "https://tts.wangwangit.com";
    /** 默认音色：zh-CN-XiaoxiaoNeural 晓晓（温柔女声，Edge TTS 最常用） */
    private static final String DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural";
    /** 文本长度上限（Edge TTS 单次请求上限较宽，保守取 3000） */
    private static final int MAX_TEXT_LENGTH = 3000;

    @Override
    public byte[] synthesize(String text, String voice) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文本不能为空");
        }
        // 截断超长文本
        String input = text.length() > MAX_TEXT_LENGTH
                ? text.substring(0, MAX_TEXT_LENGTH)
                : text;

        // 端点与音色配置：ModelConfig 可选，缺失时用默认值
        String ttsEndpoint = DEFAULT_TTS_ENDPOINT;
        String voiceName = DEFAULT_VOICE;
        ModelConfig config = modelConfigService.getActiveRaw();
        if (config != null) {
            if (config.getTtsEndpoint() != null && !config.getTtsEndpoint().isBlank()) {
                ttsEndpoint = config.getTtsEndpoint();
            }
            if (config.getTtsVoice() != null && !config.getTtsVoice().isBlank()) {
                voiceName = config.getTtsVoice();
            }
        }
        // 请求传入的 voice 优先级最高
        if (voice != null && !voice.isBlank()) {
            voiceName = voice;
        }

        String url = buildSpeechUrl(ttsEndpoint);
        log.info("[TTS] 调用 Edge-TTS url={} voice={} textLen={}", url, voiceName, input.length());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Edge-TTS / OpenAI 兼容请求体
            Map<String, Object> body = new HashMap<>();
            body.put("input", input);
            body.put("voice", voiceName);
            body.put("speed", 1.0);
            body.put("pitch", "0");
            body.put("style", "general");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            // 响应直接是音频二进制（mp3），无需解析 JSON 再下载
            ResponseEntity<byte[]> resp = restTemplate.postForEntity(url, entity, byte[].class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null || resp.getBody().length == 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR,
                        "Edge-TTS 返回异常: HTTP " + resp.getStatusCode());
            }
            byte[] audio = resp.getBody();
            log.info("[TTS] 合成成功 bytes={}", audio.length);
            return audio;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[TTS] 调用失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "TTS 不可用: " + e.getMessage());
        }
    }

    /**
     * 拼接 Edge-TTS 接口 URL：{endpoint}/v1/audio/speech
     * 兼容 endpoint 带/不带尾部斜杠、带/不带 /v1 后缀的情况。
     */
    private String buildSpeechUrl(String endpoint) {
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (base.endsWith("/v1")) {
            return base + "/audio/speech";
        }
        return base + "/v1/audio/speech";
    }
}
