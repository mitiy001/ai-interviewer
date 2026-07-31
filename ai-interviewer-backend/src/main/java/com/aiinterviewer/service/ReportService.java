package com.aiinterviewer.service;

import com.aiinterviewer.dto.resp.ReportResp;

/**
 * 面试报告 Service：持久化 summary 节点产出的报告 + 查询报告。
 */
public interface ReportService {

    /**
     * 持久化报告：解析 summary 节点产出的 report JSON，写入 interview_report 表（upsert）。
     * <p>
     * 由 InterviewExecutor 在面试结束时调用。
     *
     * @param interviewId 面试 ID
     * @param totalScore  总分
     * @param reportJson  summary 节点产出的原始 JSON
     */
    void save(Long interviewId, int totalScore, String reportJson);

    /**
     * 查询完整报告：总评 + 改进点 + 各题答题明细。
     *
     * @param interviewId 面试 ID
     * @return 报告响应（不含 HTML）
     */
    ReportResp getReport(Long interviewId);
}
