package com.aiinterviewer.dto.req;

import lombok.Data;

/**
 * 启动面试请求。
 * <p>
 * 所有字段可选：未传则使用当前用户最新上传的简历/题库、激活的模型配置/Skill、默认轮次上限。
 */
@Data
public class StartReq {

    /** 指定简历 ID（可选，默认取最新一份） */
    private Long resumeId;

    /** 指定题库 ID（可选，默认取最新一份） */
    private Long bankId;

    /** 指定模型配置 ID（可选，默认取激活的） */
    private Long modelConfigId;

    /** 指定 Skill ID（可选，默认取激活的） */
    private Long skillId;

    /** 轮次上限（可选，默认 5，不超过题库题目数） */
    private Integer maxTurns;
}
