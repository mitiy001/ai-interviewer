package com.aiinterviewer.graph.nodes;

import com.aiinterviewer.graph.prompt.PromptLoader;
import com.aiinterviewer.graph.state.InterviewState;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 出题节点：按轮次从题库取下一题，结合简历/上轮判定生成提问。
 * <p>
 * 输入 state：QUESTIONS, TURN_INDEX(从0开始), RESUME_TEXT, POSITION, HISTORY, LAST_JUDGEMENT, MODEL_CONFIG_ID
 * 输出 state：PHASE=QUESTION, TURN_INDEX+1, CURRENT_QUESTION_ID, CURRENT_QUESTION, STANDARD_ANSWER, SCORING_POINTS, AI_OUTPUT, MESSAGES, HISTORY
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionNode {

    private final NodeSupport nodeSupport;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;

    public Map<String, Object> apply(OverAllState state) {
        int turnIndex = state.value(InterviewState.TURN_INDEX, 0);
        int newTurn = turnIndex + 1;

        JsonNode question = pickQuestion(state, newTurn - 1);
        Long questionId = question.path("id").asLong();
        String content = question.path("content").asText("");
        String standardAnswer = question.path("standard_answer").asText("");
        String scoringPoints = question.path("scoring_points").asText("");

        String position = nodeSupport.text(state, InterviewState.POSITION);
        String level = nodeSupport.text(state, InterviewState.LEVEL);
        String resumeSummary = nodeSupport.text(state, InterviewState.RESUME_TEXT);
        String history = nodeSupport.text(state, InterviewState.HISTORY);
        String lastJudgement = nodeSupport.text(state, InterviewState.LAST_JUDGEMENT);

        String prompt = promptLoader.render("question", Map.of(
                "position", position.isEmpty() ? "Java" : position,
                "level", level.isEmpty() ? "mid" : level,
                "resume_summary", resumeSummary.isEmpty() ? "(未提供简历)" : resumeSummary,
                "question_content", content,
                "standard_answer", standardAnswer,
                "history", history.isEmpty() ? "(无)" : history,
                "last_judgement", lastJudgement.isEmpty() ? "(首轮，无上轮判定)" : lastJudgement
        ));

        log.info("[node:question] 轮次 {} 出题 questionId={}", newTurn, questionId);
        ChatClient client = nodeSupport.getChatClient(state);
        String aiQuestion = callLlm(client, prompt);
        if (aiQuestion == null || aiQuestion.isBlank()) {
            aiQuestion = content; // 兜底：直接用原题
        }

        String message = "AI: " + aiQuestion;
        String newHistory = appendHistory(history, message);

        Map<String, Object> result = new HashMap<>();
        result.put(InterviewState.PHASE, "QUESTION");
        result.put(InterviewState.TURN_INDEX, newTurn);
        result.put(InterviewState.CURRENT_QUESTION_ID, questionId);
        result.put(InterviewState.CURRENT_QUESTION, aiQuestion);
        result.put(InterviewState.STANDARD_ANSWER, standardAnswer);
        result.put(InterviewState.SCORING_POINTS, scoringPoints);
        result.put(InterviewState.AI_OUTPUT, aiQuestion);
        result.put(InterviewState.MESSAGES, message);
        result.put(InterviewState.HISTORY, newHistory);
        return result;
    }

    /** 取第 idx 题（0-based），越界抛异常（编排层应避免走到这里） */
    private JsonNode pickQuestion(OverAllState state, int idx) {
        String questionsJson = nodeSupport.text(state, InterviewState.QUESTIONS);
        try {
            JsonNode arr = objectMapper.readTree(questionsJson);
            if (!arr.isArray() || arr.size() == 0) {
                throw new IllegalStateException("题库为空，无法出题");
            }
            if (idx >= arr.size()) {
                throw new IllegalStateException("题目已用完 (idx=" + idx + ", total=" + arr.size() + ")");
            }
            return arr.get(idx);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析题库 JSON 失败: " + e.getMessage(), e);
        }
    }

    protected String callLlm(ChatClient client, String prompt) {
        try {
            return client.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("[node:question] LLM 调用失败", e);
            return null;
        }
    }

    private String appendHistory(String oldHistory, String newMessage) {
        return oldHistory == null || oldHistory.isEmpty() ? newMessage : oldHistory + "\n" + newMessage;
    }
}
