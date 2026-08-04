package com.aiinterviewer.service;

import com.aiinterviewer.dto.req.StartReq;
import com.aiinterviewer.dto.resp.InterviewListItemResp;
import com.aiinterviewer.dto.resp.InterviewResumeResp;
import com.aiinterviewer.dto.resp.StartResp;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 面试 Service
 */
public interface InterviewService {

    StartResp start(StartReq req);

    SseEmitter stream(Long interviewId);

    void answer(Long interviewId, String answer);

    List<InterviewListItemResp> list();

    InterviewResumeResp resume(Long interviewId);

    void abort(Long interviewId);

    void delete(Long interviewId);
}
