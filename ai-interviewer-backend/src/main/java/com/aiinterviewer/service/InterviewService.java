package com.aiinterviewer.service;

import com.aiinterviewer.dto.req.StartReq;
import com.aiinterviewer.dto.resp.InterviewListItemResp;
import com.aiinterviewer.dto.resp.StartResp;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 面试 Service
 */
public interface InterviewService {

    /**
     * 启动面试：校验前置条件（简历/题库/模型/Skill），创建面试记录。
     *
     * @param req 启动参数（可选字段用默认值）
     * @return 面试记录 ID + 提示
     */
    StartResp start(StartReq req);

    /**
     * 连接面试 SSE 流：返回 SseEmitter，并在后台驱动面试主循环。
     * <p>
     * 前端用 EventSource 连接该端点，接收 AI 消息 / 判定 / 总结等事件。
     *
     * @param interviewId 面试记录 ID
     * @return SSE emitter
     */
    SseEmitter stream(Long interviewId);

    /**
     * 提交用户回答。在收到 wait_answer 事件后调用，喂给当前轮判定。
     *
     * @param interviewId 面试记录 ID
     * @param answer      用户回答文本
     */
    void answer(Long interviewId, String answer);

    /**
     * 列出当前用户的面试历史（按开始时间倒序）。
     */
    List<InterviewListItemResp> list();

    /**
     * 中断面试：标记 SSE 会话为非活跃，同步更新数据库状态为 ABORTED。
     * <p>
     * 用户主动退出面试页面时调用，确保状态及时更新，避免 RUNNING 残留导致无法删除。
     * 幂等：对已结束的面试调用无副作用。
     *
     * @param interviewId 面试记录 ID
     */
    void abort(Long interviewId);

    /**
     * 删除面试记录及其关联数据（答题记录、报告）。
     *
     * @param interviewId 面试记录 ID
     */
    void delete(Long interviewId);
}
