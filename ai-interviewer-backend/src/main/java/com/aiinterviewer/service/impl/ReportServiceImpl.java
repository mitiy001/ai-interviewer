package com.aiinterviewer.service.impl;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.common.UserContext;
import com.aiinterviewer.dto.resp.AnswerItemResp;
import com.aiinterviewer.dto.resp.ImprovementResp;
import com.aiinterviewer.dto.resp.ReportResp;
import com.aiinterviewer.dto.resp.SalaryOfferResp;
import com.aiinterviewer.dto.resp.SalaryRangeResp;
import com.aiinterviewer.entity.AnswerRecord;
import com.aiinterviewer.entity.InterviewRecord;
import com.aiinterviewer.entity.InterviewReport;
import com.aiinterviewer.entity.Skill;
import com.aiinterviewer.exception.BusinessException;
import com.aiinterviewer.mapper.AnswerRecordMapper;
import com.aiinterviewer.mapper.InterviewRecordMapper;
import com.aiinterviewer.mapper.InterviewReportMapper;
import com.aiinterviewer.mapper.SkillMapper;
import com.aiinterviewer.service.ReportService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 面试报告 Service 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final InterviewReportMapper interviewReportMapper;
    private final InterviewRecordMapper interviewRecordMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final SkillMapper skillMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void save(Long interviewId, int totalScore, String reportJson) {
        String improvementsJson = null;
        // summary 始终存原始 JSON，便于 getReport 时解析 salary_range/salary_offer 等结构化字段
        String summaryToStore = reportJson;

        // 解析 report JSON 提取 improvements（单独存储便于查询）
        if (reportJson != null && !reportJson.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(reportJson);
                JsonNode improvements = node.path("improvements");
                if (improvements.isArray() && improvements.size() > 0) {
                    improvementsJson = objectMapper.writeValueAsString(improvements);
                }
            } catch (Exception e) {
                log.warn("解析报告 JSON 失败，按原文存储 interviewId={}: {}", interviewId, e.getMessage());
            }
        }

        // upsert（interview_id 唯一键）
        InterviewReport existing = interviewReportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getInterviewId, interviewId));
        if (existing == null) {
            InterviewReport report = new InterviewReport();
            report.setInterviewId(interviewId);
            report.setTotalScore(totalScore);
            report.setSummary(summaryToStore);
            report.setImprovementPoints(improvementsJson);
            report.setGeneratedAt(LocalDateTime.now());
            interviewReportMapper.insert(report);
        } else {
            existing.setTotalScore(totalScore);
            existing.setSummary(summaryToStore);
            existing.setImprovementPoints(improvementsJson);
            existing.setGeneratedAt(LocalDateTime.now());
            interviewReportMapper.updateById(existing);
        }
        log.info("报告已持久化 interviewId={} totalScore={}", interviewId, totalScore);
    }

    @Override
    public ReportResp getReport(Long interviewId) {
        InterviewRecord record = mustLoadOwned(interviewId);

        ReportResp resp = new ReportResp();
        resp.setInterviewId(interviewId);
        resp.setStatus(record.getStatus());
        resp.setTotalScore(record.getTotalScore());
        resp.setMaxTurns(record.getMaxTurns());

        // 报告主表
        InterviewReport report = interviewReportMapper.selectOne(
                new LambdaQueryWrapper<InterviewReport>()
                        .eq(InterviewReport::getInterviewId, interviewId));
        if (report != null) {
            resp.setSummary(report.getSummary());
            resp.setGeneratedAt(report.getGeneratedAt());
            // 从 skill 加载岗位名称
            if (record.getSkillId() != null) {
                Skill skill = skillMapper.selectById(record.getSkillId());
                if (skill != null && skill.getPosition() != null) {
                    resp.setPosition(skill.getPosition());
                }
            }
            // improvement_points 是 JSON 数组字符串
            resp.setImprovements(parseStringList(report.getImprovementPoints()));
            // 尝试从 summary 解析结构化字段（summary 可能存的是 overall_comment 文本，也可能是原始 JSON）
            applyStructuredFields(resp, report.getSummary());
        }

        // 各题答题明细
        List<AnswerRecord> answers = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getInterviewId, interviewId)
                        .orderByAsc(AnswerRecord::getTurnIndex));
        resp.setAnswers(answers.stream().map(this::toAnswerItem).collect(Collectors.toList()));
        return resp;
    }

    // ===== private =====

    private InterviewRecord mustLoadOwned(Long interviewId) {
        InterviewRecord record = interviewRecordMapper.selectById(interviewId);
        if (record == null || !UserContext.getUserId().equals(record.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "面试记录不存在");
        }
        return record;
    }

    private AnswerItemResp toAnswerItem(AnswerRecord a) {
        AnswerItemResp item = new AnswerItemResp();
        item.setId(a.getId());
        item.setTurnIndex(a.getTurnIndex());
        item.setQuestionId(a.getQuestionId());
        item.setAiQuestion(a.getAiQuestion());
        item.setUserAnswer(a.getUserAnswer());
        item.setScore(a.getScore());
        item.setJudgeReason(a.getJudgeReason());
        item.setAnsweredAt(a.getAnsweredAt());
        return item;
    }

    /** 从 summary（可能是原始 JSON）解析 overall_comment / strengths / weaknesses / improvements / salary_range */
    private void applyStructuredFields(ReportResp resp, String summary) {
        if (summary == null || summary.isBlank()) return;
        try {
            JsonNode node = objectMapper.readTree(summary);
            if (node.has("overall_comment")) {
                resp.setOverallComment(node.path("overall_comment").asText(""));
            }
            if (node.has("strengths")) {
                resp.setStrengths(parseStringList(node.path("strengths")));
            }
            if (node.has("weaknesses")) {
                resp.setWeaknesses(parseStringList(node.path("weaknesses")));
            }
            // improvements 可能是字符串数组（旧格式）或对象数组（新格式）
            JsonNode improvementsNode = node.path("improvements");
            if (improvementsNode.isArray() && improvementsNode.size() > 0) {
                if (improvementsNode.get(0).isObject()) {
                    // 新格式：对象数组
                    List<ImprovementResp> details = new ArrayList<>();
                    for (JsonNode item : improvementsNode) {
                        ImprovementResp imp = new ImprovementResp();
                        imp.setProblem(item.path("problem").asText(""));
                        imp.setLearningPath(item.path("learning_path").asText(""));
                        imp.setPractice(item.path("practice").asText(""));
                        details.add(imp);
                    }
                    resp.setImprovementDetails(details);
                } else if (resp.getImprovements() == null || resp.getImprovements().isEmpty()) {
                    // 旧格式：字符串数组
                    resp.setImprovements(parseStringList(improvementsNode));
                }
            }
            // 解析薪资范围
            JsonNode salaryNode = node.path("salary_range");
            if (salaryNode.isObject() && salaryNode.has("level")) {
                SalaryRangeResp salary = new SalaryRangeResp();
                salary.setLevel(salaryNode.path("level").asText(""));
                salary.setMonthlyMin(salaryNode.path("monthly_min").asInt(0));
                salary.setMonthlyMax(salaryNode.path("monthly_max").asInt(0));
                salary.setAnnualMin(salaryNode.path("annual_min").asInt(0));
                salary.setAnnualMax(salaryNode.path("annual_max").asInt(0));
                salary.setCurrency(salaryNode.path("currency").asText("K（人民币）"));
                salary.setNote(salaryNode.path("note").asText(""));
                resp.setSalaryRange(salary);
            }
            // 解析薪资报价（模拟真实公司 offer）
            JsonNode offerNode = node.path("salary_offer");
            if (offerNode.isObject() && offerNode.has("annual_package")) {
                SalaryOfferResp offer = new SalaryOfferResp();
                offer.setCompanyType(offerNode.path("company_type").asText(""));
                offer.setOfferLevel(offerNode.path("offer_level").asText(""));
                offer.setMonthlyBase(offerNode.path("monthly_base").asInt(0));
                offer.setMonthlyTotal(offerNode.path("monthly_total").asInt(0));
                offer.setAnnualCash(offerNode.path("annual_cash").asInt(0));
                offer.setAnnualEquity(offerNode.path("annual_equity").asInt(0));
                offer.setSignOnBonus(offerNode.path("sign_on_bonus").asInt(0));
                offer.setAnnualPackage(offerNode.path("annual_package").asInt(0));
                offer.setCurrency(offerNode.path("currency").asText("K（人民币）"));
                offer.setRationale(offerNode.path("rationale").asText(""));
                resp.setSalaryOffer(offer);
            }
        } catch (Exception e) {
            // summary 是纯文本（overall_comment），直接作为总评
            resp.setOverallComment(summary);
        }
    }

    private List<String> parseStringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> list = new ArrayList<>();
        node.forEach(n -> {
            String s = n.asText("");
            if (!s.isBlank()) list.add(s);
        });
        return list;
    }

    private List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return parseStringList(objectMapper.readTree(json));
        } catch (Exception e) {
            return List.of();
        }
    }
}
