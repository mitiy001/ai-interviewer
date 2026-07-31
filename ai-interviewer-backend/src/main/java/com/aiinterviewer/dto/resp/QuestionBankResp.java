package com.aiinterviewer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题库响应（含题目数量）
 */
@Data
public class QuestionBankResp {

    private Long id;
    private Long userId;
    private String name;
    private String source;
    private String description;
    private Integer questionCount;
    private LocalDateTime createdAt;
}
