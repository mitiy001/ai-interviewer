package com.aiinterviewer.graph.nodes;

import com.aiinterviewer.graph.prompt.PromptLoader;
import com.aiinterviewer.graph.state.InterviewState;
import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 开场白节点：生成面试开场白。
 * <p>
 * 输入 state：POSITION, RESUME_TEXT, MODEL_CONFIG_ID
 * 输出 state：PHASE=OPENING, AI_OUTPUT, MESSAGES, HISTORY
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpeningNode {

    private final NodeSupport nodeSupport;
    private final PromptLoader promptLoader;

    public Map<String, Object> apply(OverAllState state) {
        String position = nodeSupport.text(state, InterviewState.POSITION);
        String level = nodeSupport.text(state, InterviewState.LEVEL);
        String resumeSummary = nodeSupport.text(state, InterviewState.RESUME_TEXT);
        String interviewType = nodeSupport.text(state, InterviewState.INTERVIEW_TYPE);

        // 根据面试类型选择 prompt 模板
        String promptName = "HR".equals(interviewType) ? "hr_opening" : "opening";
        String prompt = promptLoader.render(promptName, Map.of(
                "position", position.isEmpty() ? "Java" : position,
                "level", level.isEmpty() ? "mid" : level,
                "resume_summary", resumeSummary.isEmpty() ? "(未提供简历)" : resumeSummary
        ));

        log.info("[node:opening] 调用 LLM 生成开场白, position={}, interviewType={}", position, interviewType);
        ChatClient client = nodeSupport.getChatClient(state);
        String opening = callLlm(client, prompt);
        if (opening == null || opening.isBlank()) {
            opening = "你好，我是本次面试的面试官，我们开始吧。请放松，依次回答问题即可。";
        }

        String message = "AI: " + opening;
        String history = appendHistory(nodeSupport.text(state, InterviewState.HISTORY), message);

        Map<String, Object> result = new HashMap<>();
        result.put(InterviewState.PHASE, "OPENING");
        result.put(InterviewState.AI_OUTPUT, opening);
        result.put(InterviewState.MESSAGES, message);
        result.put(InterviewState.HISTORY, history);
        return result;
    }

    /** 调用 LLM，异常时返回 null（由调用方兜底） */
    protected String callLlm(ChatClient client, String prompt) {
        try {
            return client.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("[node:opening] LLM 调用失败", e);
            return null;
        }
    }

    private String appendHistory(String oldHistory, String newMessage) {
        return oldHistory == null || oldHistory.isEmpty() ? newMessage : oldHistory + "\n" + newMessage;
    }
}
