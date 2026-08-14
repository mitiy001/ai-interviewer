package com.aiinterviewer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试列表项（历史记录）
 */
@Data
public class InterviewListItemResp {

    private Long id;
    /** RUNNING / FINISHED / ABORTED */
    private String status;
    private Integer maxTurns;
    private Integer totalScore;
    private String interviewType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
