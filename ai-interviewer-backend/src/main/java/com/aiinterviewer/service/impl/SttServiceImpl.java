package com.aiinterviewer.service.impl;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.entity.ModelConfig;
import com.aiinterviewer.exception.BusinessException;
import com.aiinterviewer.service.ModelConfigService;
import com.aiinterviewer.service.SttService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * STT 语音识别 Service 实现：调用 Edge-TTS 项目（https://github.com/TT3301/Edge-TTS）。
 * <p>
 * 调用规范（兼容 OpenAI Whisper API 格式）：
 *   POST {endpoint}/v1/audio/transcriptions
 *   Content-Type: multipart/form-data
 *   FormData:
 *     - file: 音频文件（mp3/wav/m4a/flac/aac/ogg/webm/amr/3gp，最大 10MB）
 *     - token: 可选，硅基流动 API Token（不传则使用服务端默认 Token）
 *   响应：{ "text": "识别出的文本" }
 * <p>
 * 引擎：硅基流动 FunAudioLLM/SenseVoiceSmall（高精度中文识别）
 * <p>
 * 端点可通过 ModelConfig.ttsEndpoint 配置（默认公共服务 https://tts.wangwangit.com）。
 * 注意：Edge-TTS 项目的 TTS 和 STT 共用同一个端点。
 * 复用 ModelConfig.ttsApiKey 作为硅基流动 Token（可选，公共服务不传也可使用）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SttServiceImpl implements SttService {

    private final ModelConfigService modelConfigService;
    private final RestTemplate restTemplate = new RestTemplate();

    /** Edge-TTS 默认公共服务端点（TTS 与 STT 共用） */
    private static final String DEFAULT_ENDPOINT = "https://tts.wangwangit.com";
    /** Edge-TTS STT 文件大小上限 10MB（硅基流动限制） */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    @Override
    public String recognize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "音频文件为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "音频文件过大（>10MB）");
        }

        // 端点配置：复用 ttsEndpoint（Edge-TTS 项目 TTS/STT 共用端点）
        String endpoint = DEFAULT_ENDPOINT;
        String siliconFlowToken = null;
        ModelConfig config = modelConfigService.getActiveRaw();
        if (config != null) {
            if (config.getTtsEndpoint() != null && !config.getTtsEndpoint().isBlank()) {
                endpoint = config.getTtsEndpoint();
            }
            // 可选：用户提供自己的硅基流动 Token 以避开公共服务限流
            String ttsApiKey = (config.getTtsApiKey() != null && !config.getTtsApiKey().isBlank())
                    ? config.getTtsApiKey() : config.getApiKey();
            // 仅当 key 不像脱敏占位符时才透传
            if (ttsApiKey != null && !ttsApiKey.isBlank() && !ttsApiKey.contains("****")) {
                siliconFlowToken = ttsApiKey;
            }
        }

        String url = buildTranscriptionUrl(endpoint);
        log.info("[STT] 调用 Edge-TTS url={} fileSize={} mimeType={}",
                url, file.getSize(), file.getContentType());

        try {
            byte[] audioBytes = file.getBytes();
            String filename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename() : "audio.webm";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });
            // token 可选：不传则使用服务端默认 Token
            if (siliconFlowToken != null) {
                body.add("token", siliconFlowToken);
            }

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<JsonNode> resp = restTemplate.postForEntity(url, entity, JsonNode.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR,
                        "Edge-TTS STT 返回异常: HTTP " + resp.getStatusCode());
            }

            JsonNode root = resp.getBody();
            String text = root.path("text").asText("");
            if (text.isBlank()) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR,
                        "语音识别结果为空: " + root);
            }
            log.info("[STT] 识别成功 textLen={}", text.length());
            return text.trim();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[STT] 调用失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "语音识别失败: " + e.getMessage());
        }
    }

    /**
     * 拼接 Edge-TTS STT 接口 URL：{endpoint}/v1/audio/transcriptions
     * 兼容 endpoint 带/不带尾部斜杠、带/不带 /v1 后缀的情况。
     */
    private String buildTranscriptionUrl(String endpoint) {
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (base.endsWith("/v1")) {
            return base + "/audio/transcriptions";
        }
        return base + "/v1/audio/transcriptions";
    }
}
