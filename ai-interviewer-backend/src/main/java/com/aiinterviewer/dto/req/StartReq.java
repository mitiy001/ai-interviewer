package com.aiinterviewer.dto.req;

import lombok.Data;

@Data
public class StartReq {

    private Long resumeId;
    private Long bankId;
    private Long modelConfigId;
    private Long skillId;
    private Integer maxTurns;
    private String interviewType;
}