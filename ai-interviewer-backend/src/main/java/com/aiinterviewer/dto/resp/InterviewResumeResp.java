package com.aiinterviewer.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 面试恢复响应（断线重连）。
 * <p>
 * 前端在 SSE 断开后调用 GET /api/interview/{id}/resume 获取此响应，
 * 用其中的消息历史重建聊天界面，然后重新连接 SSE 流继续面试。
 */
@Data
public class InterviewResumeResp {

    /** 面试记录 ID */
    private Long interviewId;

    /** 面试状态 RUNNING / FINISHED / ABORTED */
    private String status;

    /** 当前阶段：IDLE / OPENING / QUESTION / WAIT_ANSWER / JUDGE / SUMMARY / RECOVERING */
    private String phase;

    /** 当前轮次索引（从 0 开始） */
    private Integer turnIndex;

    /** 总轮次上限 */
    private Integer maxTurns;

    /** 是否正在等待用户回答 */
    private Boolean waitingAnswer;

    /** 当前问题文本（仅在 WAIT_ANSWER 阶段有值） */
    private String currentQuestion;

    /** 消息历史（用于重建聊天界面） */
    private List<MessageItem> messages;

    /** 单条消息 */
    @Data
    public static class MessageItem {

        /** 消息角色：ai / user / system */
        private String role;

        /** 消息内容 */
        private String content;

        /** 轮次索引（可选） */
        private Integer turn;

        /** 评分（可选，仅 system 评分消息有值） */
        private Integer score;

        /** 判定理由（可选） */
        private String judgeReason;
    }
}
