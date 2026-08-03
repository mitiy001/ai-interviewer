package com.aiinterviewer.controller;

import com.aiinterviewer.common.Result;
import com.aiinterviewer.dto.req.AnswerReq;
import com.aiinterviewer.dto.req.PracticeReq;
import com.aiinterviewer.dto.req.StartReq;
import com.aiinterviewer.dto.resp.InterviewListItemResp;
import com.aiinterviewer.dto.resp.PracticeQuestionResp;
import com.aiinterviewer.dto.resp.ReportResp;
import com.aiinterviewer.dto.resp.StartResp;
import com.aiinterviewer.service.InterviewService;
import com.aiinterviewer.service.PracticeService;
import com.aiinterviewer.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 面试 REST 接口
 * <p>
 * <ul>
 *   <li>POST /api/interview/start              启动面试（校验前置条件 + 建记录）</li>
 *   <li>GET  /api/interview                    面试历史列表</li>
 *   <li>GET  /api/interview/{id}/stream        连接 SSE 流（驱动面试主循环）</li>
 *   <li>POST /api/interview/{id}/answer        提交本轮回答</li>
 *   <li>GET  /api/interview/{id}/report        查询面试报告</li>
 *   <li>POST /api/interview/{id}/practice      生成错题重练题目</li>
 *   <li>DELETE /api/interview/{id}             删除面试记录及关联数据</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final ReportService reportService;
    private final PracticeService practiceService;

    @PostMapping("/start")
    public Result<StartResp> start(@RequestBody @Valid StartReq req) {
        return Result.ok(interviewService.start(req));
    }

    @GetMapping
    public Result<List<InterviewListItemResp>> list() {
        return Result.ok(interviewService.list());
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id) {
        return interviewService.stream(id);
    }

    @PostMapping("/{id}/answer")
    public Result<Void> answer(@PathVariable Long id, @RequestBody @Valid AnswerReq req) {
        interviewService.answer(id, req.getAnswer());
        return Result.ok();
    }

    @GetMapping("/{id}/report")
    public Result<ReportResp> report(@PathVariable Long id) {
        return Result.ok(reportService.getReport(id));
    }

    @PostMapping("/{id}/practice")
    public Result<List<PracticeQuestionResp>> practice(@PathVariable Long id,
                                                        @RequestBody(required = false) PracticeReq req) {
        int sa = (req == null || req.getShortAnswerCount() <= 0) ? 2 : req.getShortAnswerCount();
        int cc = (req == null || req.getCodeCount() <= 0) ? 0 : req.getCodeCount();
        return Result.ok(practiceService.generate(id, sa, cc));
    }

    @PostMapping("/{id}/abort")
    public Result<Void> abort(@PathVariable Long id) {
        interviewService.abort(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        interviewService.delete(id);
        return Result.ok();
    }
}
