package com.aiinterviewer.service.impl;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.common.UserContext;
import com.aiinterviewer.dto.resp.PracticeQuestionResp;
import com.aiinterviewer.entity.AnswerRecord;
import com.aiinterviewer.entity.InterviewRecord;
import com.aiinterviewer.exception.BusinessException;
import com.aiinterviewer.graph.prompt.PromptLoader;
import com.aiinterviewer.graph.nodes.NodeSupport;
import com.aiinterviewer.mapper.AnswerRecordMapper;
import com.aiinterviewer.mapper.InterviewRecordMapper;
import com.aiinterviewer.service.PracticeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 错题重练 Service 实现：取面试中得分较低的题目，调用 LLM 生成练习题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeServiceImpl implements PracticeService {

    /** 得分低于满分 60% 视为薄弱题 */
    private static final double WEAK_THRESHOLD_RATIO = 0.6;
    /** Skill 满分（与 judge prompt 中一致） */
    private static final int FULL_SCORE_PER_TURN = 10;

    private final InterviewRecordMapper interviewRecordMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final NodeSupport nodeSupport;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;

    @Override
    public List<PracticeQuestionResp> generate(Long interviewId) {
        // 1. 校验面试记录归属
        InterviewRecord record = interviewRecordMapper.selectById(interviewId);
        if (record == null || !UserContext.getUserId().equals(record.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "面试记录不存在");
        }

        // 2. 取答题明细
        List<AnswerRecord> answers = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getInterviewId, interviewId)
                        .orderByAsc(AnswerRecord::getTurnIndex));
        if (answers.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该面试无答题记录，无法生成练习题");
        }

        // 3. 筛选薄弱题（得分 < 60% 满分，或未作答）
        int threshold = (int) Math.ceil(FULL_SCORE_PER_TURN * WEAK_THRESHOLD_RATIO);
        List<AnswerRecord> weak = answers.stream()
                .filter(a -> a.getScore() == null || a.getScore() < threshold)
                .collect(Collectors.toList());
        if (weak.isEmpty()) {
            // 若都答得不错，取得分最低的 2 题
            weak = answers.stream()
                    .sorted((a, b) -> {
                        int sa = a.getScore() == null ? -1 : a.getScore();
                        int sb = b.getScore() == null ? -1 : b.getScore();
                        return Integer.compare(sa, sb);
                    })
                    .limit(2)
                    .collect(Collectors.toList());
        }

        // 4. 拼接薄弱题描述
        StringBuilder weakDesc = new StringBuilder();
        for (AnswerRecord a : weak) {
            weakDesc.append("【题目】").append(a.getAiQuestion()).append("\n");
            weakDesc.append("【候选人回答】").append(a.getUserAnswer() == null || a.getUserAnswer().isBlank() ? "(未作答)" : a.getUserAnswer()).append("\n");
            weakDesc.append("【得分】").append(a.getScore() == null ? "未评分" : a.getScore() + "/" + FULL_SCORE_PER_TURN).append("\n");
            weakDesc.append("【判定理由】").append(a.getJudgeReason() == null ? "(无)" : a.getJudgeReason()).append("\n\n");
        }

        // 5. 调用 LLM 生成练习题
        String position = record.getBankId() == null ? "Java" : "Java";
        String prompt = promptLoader.render("practice", Map.of(
                "position", position,
                "weak_answers", weakDesc.toString()
        ));

        log.info("生成错题重练 interviewId={} weakCount={}", interviewId, weak.size());
        ChatClient client = nodeSupport.getChatClientByConfigId(record.getModelConfigId());
        String llmResult;
        try {
            llmResult = client.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("生成练习题 LLM 调用失败 interviewId={}", interviewId, e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "生成练习题失败: " + e.getMessage());
        }

        // 6. 解析 JSON
        return parseQuestions(llmResult);
    }

    private List<PracticeQuestionResp> parseQuestions(String llmResult) {
        if (llmResult == null || llmResult.isBlank()) {
            return List.of();
        }
        String cleaned = cleanMarkdownFence(llmResult);
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            JsonNode arr = root.path("questions");
            if (!arr.isArray()) return List.of();
            List<PracticeQuestionResp> list = new ArrayList<>();
            for (JsonNode q : arr) {
                PracticeQuestionResp resp = new PracticeQuestionResp();
                resp.setType(q.path("type").asText("short_answer"));
                resp.setQuestion(q.path("question").asText(""));
                resp.setAnswer(q.path("answer").asText(""));
                resp.setExplanation(q.path("explanation").asText(""));
                resp.setKnowledgePoint(q.path("knowledge_point").asText(""));
                // 解析选项
                JsonNode options = q.path("options");
                if (options.isArray()) {
                    List<String> opts = new ArrayList<>();
                    options.forEach(o -> opts.add(o.asText("")));
                    resp.setOptions(opts);
                }
                list.add(resp);
            }
            return list;
        } catch (Exception e) {
            log.warn("解析练习题 JSON 失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String cleanMarkdownFence(String s) {
        String cleaned = s.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```\\w*\\s*", "").replaceAll("\\s*```$", "").trim();
        }
        return cleaned;
    }
}
