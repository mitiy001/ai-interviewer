package com.aiinterviewer.dto.resp;

import lombok.Data;

/**
 * 模型连通测试结果
 */
@Data
public class ModelTestResp {

    /** 是否连通成功 */
    private Boolean success;
    /** 提示信息（失败时为错误原因，成功时为简短说明） */
    private String message;
    /** 模型实际回复内容（成功时） */
    private String reply;
    /** 调用耗时（毫秒） */
    private Long latencyMs;

    public static ModelTestResp ok(String reply, long latencyMs) {
        ModelTestResp r = new ModelTestResp();
        r.setSuccess(true);
        r.setMessage("连通成功");
        r.setReply(reply);
        r.setLatencyMs(latencyMs);
        return r;
    }

    public static ModelTestResp fail(String message, long latencyMs) {
        ModelTestResp r = new ModelTestResp();
        r.setSuccess(false);
        r.setMessage(message);
        r.setReply("");
        r.setLatencyMs(latencyMs);
        return r;
    }
}
