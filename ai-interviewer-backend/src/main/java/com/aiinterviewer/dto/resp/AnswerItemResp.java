package com.aiinterviewer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单题答题记录（报告内嵌）
 */
@Data
public class AnswerItemResp {

    private Long id;
    private Integer turnIndex;
    private Long questionId;
    /** AI 提问内容 */
    private String aiQuestion;
    /** 用户回答 */
    private String userAnswer;
    /** 本题得分 */
    private Integer score;
    /** 判定理由 */
    private String judgeReason;
    private LocalDateTime answeredAt;
}
