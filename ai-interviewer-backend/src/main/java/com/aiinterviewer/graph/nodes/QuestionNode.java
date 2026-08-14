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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 出题节点：从题库中随机选取未使用过的题目，结合简历/上轮判定生成提问。
 * <p>
 * 输入 state：QUESTIONS, TURN_INDEX(从0开始), RESUME_TEXT, POSITION, HISTORY, LAST_JUDGEMENT, MODEL_CONFIG_ID, INTERVIEW_TYPE, USED_QUESTION_IDS
 * 输出 state：PHASE=QUESTION, TURN_INDEX+1, CURRENT_QUESTION_ID, CURRENT_QUESTION, STANDARD_ANSWER, SCORING_POINTS, AI_OUTPUT, MESSAGES, HISTORY, USED_QUESTION_IDS
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

        JsonNode question = pickUnusedQuestion(state);
        Long questionId = question.path("id").asLong();
        String content = question.path("content").asText("");
        String standardAnswer = question.path("standard_answer").asText("");
        String scoringPoints = question.path("scoring_points").asText("");

        String position = nodeSupport.text(state, InterviewState.POSITION);
        String level = nodeSupport.text(state, InterviewState.LEVEL);
        String resumeSummary = nodeSupport.text(state, InterviewState.RESUME_TEXT);
        String history = nodeSupport.text(state, InterviewState.HISTORY);
        String lastJudgement = nodeSupport.text(state, InterviewState.LAST_JUDGEMENT);
        String interviewType = nodeSupport.text(state, InterviewState.INTERVIEW_TYPE);

// 根据面试类型选择 prompt 模板
        String promptName = "HR".equals(interviewType) ? "hr_question" : "question";
        String prompt = promptLoader.render(promptName, Map.of(
                "position", position.isEmpty() ? "Java" : position,
                "level", level.isEmpty() ? "mid" : level,
                "resume_summary", resumeSummary.isEmpty() ? "(未提供简历)" : resumeSummary,
                "question_content", content,
                "standard_answer", standardAnswer,
                "history", history.isEmpty() ? "(无)" : history,
                "last_judgement", lastJudgement.isEmpty() ? "(首轮，无上轮判定)" : lastJudgement
        ));

        log.info("[node:question] 轮次 {} 出题 questionId={} interviewType={}", newTurn, questionId, interviewType);
        ChatClient client = nodeSupport.getChatClient(state);
        String aiQuestion = callLlm(client, prompt);
        if (aiQuestion == null || aiQuestion.isBlank()) {
            aiQuestion = content;
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
// 记录已用题目 ID
        result.put(InterviewState.USED_QUESTION_IDS, questionId);
        return result;
    }

/** 从题库中随机选取一个未使用过的题目 */
    @SuppressWarnings("unchecked")
    private JsonNode pickUnusedQuestion(OverAllState state) {
        String questionsJson = nodeSupport.text(state, InterviewState.QUESTIONS);
        try {
            JsonNode arr = objectMapper.readTree(questionsJson);
            if (!arr.isArray() || arr.size() == 0) {
                throw new IllegalStateException("题库为空，无法出题");
            }

// 获取已用题目 ID 集合
            Object usedObj = state.value(InterviewState.USED_QUESTION_IDS, new ArrayList<Long>());
            Set<Long> usedIds = new HashSet<>();
            if (usedObj instanceof List) {
                for (Object o : (List<Object>) usedObj) {
                    if (o instanceof Number n) {
                        usedIds.add(n.longValue());
                    }
                }
            }

// 过滤未使用的题目
            List<JsonNode> available = new ArrayList<>();
            for (JsonNode q : arr) {
                long qid = q.path("id").asLong();
                if (!usedIds.contains(qid)) {
                    available.add(q);
                }
            }

            if (available.isEmpty()) {
                throw new IllegalStateException("所有题目已使用完毕，无法继续出题");
            }

// 从剩余题目中随机选取
            Collections.shuffle(available);
            JsonNode picked = available.get(0);
            log.info("从 {} 道可用题目中随机选取 questionId={}", available.size(), picked.path("id").asLong());
            return picked;
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