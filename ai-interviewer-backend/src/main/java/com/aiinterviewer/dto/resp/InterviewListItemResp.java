package com.aiinterviewer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InterviewListItemResp {

    private Long id;
    private String status;
    private Integer maxTurns;
    private Integer totalScore;
    private String interviewType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}