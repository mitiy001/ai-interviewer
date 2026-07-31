package com.aiinterviewer.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模型配置请求
 */
@Data
public class ModelConfigReq {

    /**
     * 编辑场景下传入（用于连通测试时回查数据库已保存的 apiKey）；
     * 新建场景留空。保存接口（create/update）忽略此字段。
     */
    private Long id;

    @NotBlank(message = "name 不能为空")
    @Size(max = 64, message = "name 最长 64")
    private String name;

    @NotBlank(message = "provider 不能为空")
    @Size(max = 32, message = "provider 最长 32")
    private String provider;

    @NotBlank(message = "api_key 不能为空")
    @Size(max = 256, message = "api_key 最长 256")
    private String apiKey;

    @NotBlank(message = "model 不能为空")
    @Size(max = 64, message = "model 最长 64")
    private String model;

    @NotBlank(message = "endpoint 不能为空")
    @Size(max = 256, message = "endpoint 最长 256")
    private String endpoint;

    @Size(max = 64, message = "judge_model 最长 64")
    private String judgeModel;

    @Size(max = 256, message = "judge_endpoint 最长 256")
    private String judgeEndpoint;

    @Size(max = 256, message = "tts_endpoint 最长 256")
    private String ttsEndpoint;

    @Size(max = 256, message = "tts_api_key 最长 256")
    private String ttsApiKey;

    @Size(max = 64, message = "tts_model 最长 64")
    private String ttsModel;

    @Size(max = 64, message = "tts_voice 最长 64")
    private String ttsVoice;

    /** 1=激活 0=未激活；新增/更新时若为 1，会自动取消其他激活 */
    private Integer isActive;
}
