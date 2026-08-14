package com.aiinterviewer.dto.req;

import lombok.Data;

@Data
public class StartReq {

    private Long resumeId;
    private Long bankId;
    private Long modelConfigId;
    private Long skillId;
    private Integer maxTurns;
/** 面试类型：TECH（技术面）/ HR（人事面），默认 TECH */
    private String interviewType;
}
