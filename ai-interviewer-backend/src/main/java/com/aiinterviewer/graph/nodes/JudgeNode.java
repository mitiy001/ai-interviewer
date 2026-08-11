package com.aiinterviewer.graph.nodes;

import com.aiinterviewer.entity.AnswerRecord;
import com.aiinterviewer.graph.prompt.PromptLoader;
import com.aiinterviewer.graph.state.InterviewState;
import com.aiinterviewer.mapper.AnswerRecordMapper;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeNode {

    private final NodeSupport nodeSupport;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;
    private final AnswerRecordMapper answerRecordMapper;

    public Map<String, Object> apply(OverAllState state) {
        String currentQuestion = nodeSupport.text(state, InterviewState.CURRENT_QUESTION);
        String standardAnswer = nodeSupport.text(state, InterviewState.STANDARD_ANSWER);
        String scoringPoints = nodeSupport.text(state, InterviewState.SCORING_POINTS);
        String userAnswer = nodeSupport.text(state, InterviewState.USER_ANSWER);
        String skillPrompt = nodeSupport.text(state, InterviewState.SKILL_PROMPT);
        String position = nodeSupport.text(state, InterviewState.POSITION);
        String level = nodeSupport.text(state, InterviewState.LEVEL);
        String interviewType = nodeSupport.text(state, InterviewState.INTERVIEW_TYPE);

        String promptName = "HR".equals(interviewType) ? "hr_judge" : "judge";
        String prompt = promptLoader.render(promptName, Map.of(
                "skill_prompt", skillPrompt,
                "position", position.isEmpty() ? "Java" : position,
                "level", level.isEmpty() ? "mid" : level,
                "question_content", currentQuestion,
                "standard_answer", standardAnswer,
                "scoring_points", scoringPoints,
                "user_answer", userAnswer.isEmpty() ? "(未作答)" : userAnswer
        ));

        log.info("[node:judge] 调用 LLM 判定, interviewType={}", interviewType);
        ChatClient client = nodeSupport.getChatClient(state);
        String llmResult = callLlm(client, prompt);

        JudgeResult parsed = parseJudgement(llmResult);
        String judgementJson = parsed.rawJson != null ? parsed.rawJson : llmResult;

        saveAnswerRecord(state, parsed);

        String aiText = parsed.reason != null ? parsed.reason : "判定完成";
        String message = "AI: " + aiText;
        String history = appendHistory(
                nodeSupport.text(state, InterviewState.HISTORY),
                "USER: " + (userAnswer.isEmpty() ? "(未作答)" : userAnswer) + "\n" + message
        );

        Map<String, Object> result = new HashMap<>();
        result.put(InterviewState.PHASE, "JUDGE");
        result.put(InterviewState.AI_OUTPUT, aiText);
        result.put(InterviewState.JUDGEMENTS, judgementJson);
        result.put(InterviewState.SCORES, parsed.score);
        result.put(InterviewState.LAST_JUDGEMENT, judgementJson);
        result.put(InterviewState.MESSAGES, message);
        result.put(InterviewState.HISTORY, history);
        return result;
    }

    private JudgeResult parseJudgement(String llmResult) {
        JudgeResult r = new JudgeResult();
        if (llmResult == null || llmResult.isBlank()) {
            r.score = 0;
            r.reason = "判定失败：LLM 未返回内容";
            return r;
        }
        String cleaned = llmResult.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```\\w*\\s*", "").replaceAll("\\s*```$", "").trim();
        }
        try {
            JsonNode node = objectMapper.readTree(cleaned);
            r.rawJson = cleaned;
            r.score = node.path("score").asInt(0);
            r.reason = node.path("reason").asText("判定完成");
            return r;
        } catch (Exception e) {
            log.warn("[node:judge] LLM 返回非 JSON，原样保留: {}", llmResult);
            r.rawJson = llmResult;
            r.score = 0;
            r.reason = "判定解析失败，原始内容已保留";
            return r;
        }
    }

    private void saveAnswerRecord(OverAllState state, JudgeResult parsed) {
        try {
            AnswerRecord record = new AnswerRecord();
            record.setInterviewId(state.value(InterviewState.INTERVIEW_ID, (Long) null));
            record.setQuestionId(state.value(InterviewState.CURRENT_QUESTION_ID, (Long) null));
            record.setTurnIndex(state.value(InterviewState.TURN_INDEX, 0));
            record.setUserAnswer(nodeSupport.text(state, InterviewState.USER_ANSWER));
            record.setAiQuestion(nodeSupport.text(state, InterviewState.CURRENT_QUESTION));
            record.setScore(parsed.score);
            record.setJudgeReason(parsed.reason);
            record.setAnsweredAt(LocalDateTime.now());
            answerRecordMapper.insert(record);
            log.info("[node:judge] 保存答题记录 interviewId={} turn={} score={}",
                    record.getInterviewId(), record.getTurnIndex(), record.getScore());
        } catch (Exception e) {
            log.error("[node:judge] 保存答题记录失败", e);
        }
    }

    protected String callLlm(ChatClient client, String prompt) {
        try {
            return client.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("[node:judge] LLM 调用失败", e);
            return null;
        }
    }

    private String appendHistory(String oldHistory, String newMessage) {
        return oldHistory == null || oldHistory.isEmpty() ? newMessage : oldHistory + "\n" + newMessage;
    }

    private static class JudgeResult {
        Integer score;
        String reason;
        String rawJson;
    }
}