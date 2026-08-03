package com.aiinterviewer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 面试报告响应：聚合总评 + 改进点 + 各题答题记录。
 * <p>
 * 后端只存结构化数据 + 文本，HTML 由前端 Vue 组件实时渲染（不存 HTML）。
 */
@Data
public class ReportResp {

    private Long interviewId;
    /** 面试岗位（来自 Skill） */
    private String position;
    /** 面试状态 */
    private String status;
    /** 总分 */
    private Integer totalScore;
    /** 轮次上限 */
    private Integer maxTurns;
    /** 总评（来自 report JSON 的 overall_comment） */
    private String overallComment;
    /** 亮点 */
    private List<String> strengths;
    /** 不足 */
    private List<String> weaknesses;
    /** 改进建议（旧格式：纯文本数组，兼容已存报告） */
    private List<String> improvements;
    /** 改进建议（新结构化格式） */
    private List<ImprovementResp> improvementDetails;
    /** 薪资范围估算 */
    private SalaryRangeResp salaryRange;
    /** 薪资报价（模拟真实公司 offer） */
    private SalaryOfferResp salaryOffer;
    /** 报告原始 JSON（兜底，前端可自行解析渲染） */
    private String summary;
    /** 报告生成时间 */
    private LocalDateTime generatedAt;
    /** 各题答题明细 */
    private List<AnswerItemResp> answers;
}
