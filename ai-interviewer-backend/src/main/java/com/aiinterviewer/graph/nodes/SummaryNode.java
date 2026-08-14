package com.aiinterviewer.graph.nodes;

import com.aiinterviewer.graph.prompt.PromptLoader;
import com.aiinterviewer.graph.state.InterviewState;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 总结节点：聚合各轮判定生成面试总结报告。
 * <p>
 * 输入 state：JUDGEMENTS, SCORES, POSITION, RESUME_TEXT, HISTORY, MODEL_CONFIG_ID
 * 输出 state：PHASE=SUMMARY, AI_OUTPUT, TOTAL_SCORE, REPORT, MESSAGES, HISTORY
 * 副作用：无（报告持久化由 T12 ReportService 在 graph 结束后处理）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SummaryNode {

    private final NodeSupport nodeSupport;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;

    public Map<String, Object> apply(OverAllState state) {
        String judgements = joinList(state, InterviewState.JUDGEMENTS);
        String scores = joinList(state, InterviewState.SCORES);
        int totalScore = sumScores(state);
        List<Integer> scoreList = flattenScores(state);

        String position = nodeSupport.text(state, InterviewState.POSITION);
        String level = nodeSupport.text(state, InterviewState.LEVEL);
        String resumeSummary = nodeSupport.text(state, InterviewState.RESUME_TEXT);
        String interviewType = nodeSupport.text(state, InterviewState.INTERVIEW_TYPE);

        // 计算得分率：满分 = maxTurns × 100（每题 100 分）
        int maxTurns = state.value(InterviewState.MAX_TURNS, 5);
        if (maxTurns <= 0) maxTurns = 5;
        int fullScore = maxTurns * 100;
        int scoreRatePct = fullScore > 0 ? Math.round((float) totalScore * 100 / fullScore) : 0;
        // 统计未通过轮数（score < 40）
        int failCount = 0;
        for (int s : scoreList) if (s < 40) failCount++;
        int totalQuestions = Math.max(scoreList.size(), maxTurns);

        // 根据面试类型选择 prompt 模板
        String promptName = "HR".equals(interviewType) ? "hr_summary" : "summary";
        String prompt = promptLoader.render(promptName, Map.of(
                "position", position.isEmpty() ? "Java" : position,
                "level", level.isEmpty() ? "mid" : level,
                "resume_summary", resumeSummary.isEmpty() ? "(未提供简历)" : resumeSummary,
                "judgements", judgements.isEmpty() ? "(无判定数据)" : judgements,
                "scores", scores.isEmpty() ? "(无得分)" : scores,
                "total_score", String.valueOf(totalScore),
                "max_turns", String.valueOf(maxTurns),
                "full_score", String.valueOf(fullScore),
                "score_rate", scoreRatePct + "%"
        ));

        log.info("[node:summary] 调用 LLM 生成总结, interviewType={}, totalScore={}, scoreRate={}%, failCount={}/{}",
                interviewType, totalScore, scoreRatePct, failCount, totalQuestions);
        ChatClient client = nodeSupport.getChatClient(state);
        String llmResult = callLlm(client, prompt);

        String reportJson = llmResult == null ? "{}" : cleanMarkdownFence(llmResult);
        // 后端硬性钳制薪资：防止 LLM 越界或答错仍给高薪
        reportJson = clampSalary(reportJson, scoreRatePct, failCount, totalQuestions, level);

        String overallComment = extractOverallComment(reportJson);
        if (overallComment == null || overallComment.isBlank()) {
            overallComment = "面试结束，总分 " + totalScore;
        }

        String message = "AI: " + overallComment;
        String history = appendHistory(nodeSupport.text(state, InterviewState.HISTORY), message);

        Map<String, Object> result = new HashMap<>();
        result.put(InterviewState.PHASE, "SUMMARY");
        result.put(InterviewState.AI_OUTPUT, overallComment);
        result.put(InterviewState.TOTAL_SCORE, totalScore);
        result.put(InterviewState.REPORT, reportJson);
        result.put(InterviewState.MESSAGES, message);
        result.put(InterviewState.HISTORY, history);
        return result;
    }

    @SuppressWarnings("unchecked")
    private int sumScores(OverAllState state) {
        return sumValues(state.value(InterviewState.SCORES, new ArrayList<>()));
    }

    /** 递归求和：支持 Number、List<Number>、嵌套 List */
    private int sumValues(Object scores) {
        if (scores == null) return 0;
        if (scores instanceof Number n) return n.intValue();
        if (scores instanceof List<?> list) {
            int sum = 0;
            for (Object o : list) sum += sumValues(o);
            return sum;
        }
        return 0;
    }

    /** 递归展平 SCORES 为 List<Integer>，用于统计未通过轮数 */
    @SuppressWarnings("unchecked")
    private List<Integer> flattenScores(OverAllState state) {
        List<Integer> out = new ArrayList<>();
        collectScores(state.value(InterviewState.SCORES, new ArrayList<>()), out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private void collectScores(Object v, List<Integer> out) {
        if (v == null) return;
        if (v instanceof Number n) {
            out.add(n.intValue());
        } else if (v instanceof List<?> list) {
            for (Object o : list) collectScores(o, out);
        }
    }

    // ===== 薪资硬性钳制（防止 LLM 越界或答错仍给高薪） =====

    /** 各等级月薪区间（K）：[min, max] */
    private static final int[][] MONTHLY_BOUNDS = {
            /* junior */ {6, 9},
            /* mid     */ {9, 12},
            /* senior  */ {12, 25},
    };

    /** 将 level 映射到 0/1/2，未识别默认 mid */
    private int levelIndex(String level) {
        if (level == null) return 1;
        String l = level.toLowerCase().trim();
        if (l.startsWith("junior") || l.contains("初级")) return 0;
        if (l.startsWith("senior") || l.contains("高级")) return 2;
        return 1; // mid
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * 后端硬性钳制薪资：依据得分率与未通过率，强制约束 salary_range 与 salary_offer 数值。
     * 规则：
     * - 得分率 < 50%：不发 offer（annual_package=0），薪资范围压到下限
     * - 未通过率 ≥ 50%：薪资压到区间下限
     * - 所有月薪数值钳制到等级区间内
     * - 重算 offer 公式一致性
     */
    private String clampSalary(String reportJson, int scoreRatePct, int failCount, int totalQuestions, String level) {
        if (reportJson == null || reportJson.isBlank()) return reportJson;
        try {
            JsonNode root = objectMapper.readTree(reportJson);
            int idx = levelIndex(level);
            int mMin = MONTHLY_BOUNDS[idx][0];
            int mMax = MONTHLY_BOUNDS[idx][1];
            // 年薪（万）≈ 月薪 × 12 / 10
            int aMin = (int) Math.round(mMin * 12.0 / 10.0);
            int aMax = (int) Math.round(mMax * 12.0 / 10.0);

            boolean noOffer = scoreRatePct < 50;
            double failRate = totalQuestions > 0 ? (double) failCount / totalQuestions : 0;
            boolean forceMin = noOffer || failRate >= 0.5;

            // 钳制 salary_range
            JsonNode salaryNode = root.path("salary_range");
            if (salaryNode.isObject()) {
                ObjectNode salary = (ObjectNode) salaryNode;
                int mm = forceMin ? mMin : clamp(salary.path("monthly_min").asInt(mMin), mMin, mMax);
                int mx = forceMin ? mMin : clamp(salary.path("monthly_max").asInt(mMax), mMin, mMax);
                // 保证 min ≤ max
                if (mm > mx) mx = mm;
                salary.put("monthly_min", mm);
                salary.put("monthly_max", mx);
                salary.put("annual_min", forceMin ? aMin : clamp(salary.path("annual_min").asInt(aMin), aMin, aMax));
                salary.put("annual_max", forceMin ? aMin : clamp(salary.path("annual_max").asInt(aMax), aMin, aMax));
            }

            // 钳制 salary_offer
            JsonNode offerNode = root.path("salary_offer");
            if (offerNode.isObject()) {
                ObjectNode offer = (ObjectNode) offerNode;
                if (noOffer) {
                    offer.put("annual_package", 0);
                    // 保留 company_type/offer_level/rationale 供前端展示"未发 Offer"原因
                } else {
                    int monthlyBase = forceMin ? mMin : clamp(offer.path("monthly_base").asInt(mMin), mMin, mMax);
                    offer.put("monthly_base", monthlyBase);
                    // 重算 monthly_total：保留 LLM 的绩效系数，但不低于 base
                    int llmTotal = offer.path("monthly_total").asInt(monthlyBase);
                    int monthlyTotal = clamp(Math.max(llmTotal, monthlyBase), monthlyBase, mMax + 5);
                    offer.put("monthly_total", monthlyTotal);
                    // 重算年薪现金（万）= monthly_total × 12 / 10
                    int annualCash = (int) Math.round(monthlyTotal * 12.0 / 10.0);
                    offer.put("annual_cash", annualCash);
                    int equity = offer.path("annual_equity").asInt(0);
                    int signOn = offer.path("sign_on_bonus").asInt(0);
                    // 重算年薪总包 = 年薪现金 + 股票 + 签字费
                    offer.put("annual_package", annualCash + equity + signOn);
                }
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("[node:summary] 薪资钳制失败，使用 LLM 原始输出: {}", e.getMessage());
            return reportJson;
        }
    }

    @SuppressWarnings("unchecked")
    private String joinList(OverAllState state, String key) {
        return joinValues(state.value(key, new ArrayList<>()));
    }

    /** 递归拼接：支持单值、List、嵌套 List */
    private String joinValues(Object v) {
        if (v == null) return "";
        if (v instanceof List<?> list) {
            return list.stream()
                    .map(this::joinValues)
                    .filter(s -> !s.isEmpty())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
        }
        return String.valueOf(v);
    }

    private String extractOverallComment(String reportJson) {
        try {
            JsonNode node = objectMapper.readTree(reportJson);
            return node.path("overall_comment").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private String cleanMarkdownFence(String s) {
        String cleaned = s.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("^```\\w*\\s*", "").replaceAll("\\s*```$", "").trim();
        }
        return cleaned;
    }

    protected String callLlm(ChatClient client, String prompt) {
        try {
            return client.prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("[node:summary] LLM 调用失败", e);
            return null;
        }
    }

    private String appendHistory(String oldHistory, String newMessage) {
        return oldHistory == null || oldHistory.isEmpty() ? newMessage : oldHistory + "\n" + newMessage;
    }
}
