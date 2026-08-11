package com.aiinterviewer.graph.state;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateFactory;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.HashMap;
import java.util.Map;

/**
 * 面试流程共享状态定义。
 * <p>
 * Spring AI Alibaba graph 的 OverAllState 是 Map 结构，
 * 这里集中定义所有 key、合并策略，并提供状态工厂。
 * <p>
 * 节点返回 Map&lt;String, Object&gt;，引擎按 KeyStrategy 合并到 OverAllState。
 *
 * <ul>
 *   <li>REPLACE：新值覆盖旧值（默认）</li>
 *   <li>APPEND：新值追加到 List（用于消息历史、答题记录）</li>
 * </ul>
 */
public final class InterviewState {

    private InterviewState() {
    }

    // ===== 状态 key =====

    /** 面试记录 ID（Long） */
    public static final String INTERVIEW_ID = "interview_id";
    /** 当前阶段：OPENING / QUESTION / JUDGE / SUMMARY / DONE */
    public static final String PHASE = "phase";
    /** 当前轮次序号（Integer，从 1 开始） */
    public static final String TURN_INDEX = "turn_index";
    /** 简历纯文本（String） */
    public static final String RESUME_TEXT = "resume_text";
    /** 当前题库 ID（Long） */
    public static final String BANK_ID = "bank_id";
    /** 当前题目 ID（Long，单轮） */
    public static final String CURRENT_QUESTION_ID = "current_question_id";
    /** 当前题目内容（String，单轮） */
    public static final String CURRENT_QUESTION = "current_question";
    /** AI 最新一轮输出文本（String，单轮，REPLACE） */
    public static final String AI_OUTPUT = "ai_output";
    /** 用户最新回答（String，单轮） */
    public static final String USER_ANSWER = "user_answer";
    /** 对话消息历史（List<String>，APPEND，每条 "AI: xxx" / "USER: xxx"） */
    public static final String MESSAGES = "messages";
    /** 每轮判定结果 JSON（List<String>，APPEND） */
    public static final String JUDGEMENTS = "judgements";
    /** 每轮得分（List<Integer>，APPEND） */
    public static final String SCORES = "scores";
    /** 总分（Integer，summary 阶段写入） */
    public static final String TOTAL_SCORE = "total_score";
    /** 总结报告 JSON（String） */
    public static final String REPORT = "report";
    /** 错误信息（String） */
    public static final String ERROR = "error";

    // ===== 编排辅助 key（T9/T10 节点间传递） =====

    /** 当前模型配置 ID（Long，启动时预加载） */
    public static final String MODEL_CONFIG_ID = "model_config_id";
    /** 面试岗位（String，来自 Skill.position，启动时预加载） */
    public static final String POSITION = "position";
    /** 工程师等级（String，来自 Skill.level：junior/mid/senior，启动时预加载） */
    public static final String LEVEL = "level";
    /** 当前激活 Skill 的 prompt 模板（String，启动时预加载） */
    public static final String SKILL_PROMPT = "skill_prompt";
    /** 题库所有题目 JSON 数组（String，启动时预加载，每项含 id/content/standard_answer/scoring_points） */
    public static final String QUESTIONS = "questions";
    /** 当前题目的评分点 JSON（String，单轮） */
    public static final String SCORING_POINTS = "scoring_points";
    /** 当前题目的标准答案（String，单轮） */
    public static final String STANDARD_ANSWER = "standard_answer";
    /** 历史对话文本（String，累积，格式 "AI: ...\nUSER: ...\nAI: ..."） */
    public static final String HISTORY = "history";
    /** 上一轮判定 JSON（String，QuestionNode 读取以决定是否追问） */
    public static final String LAST_JUDGEMENT = "last_judgement";
    /** 剩余轮次上限（Integer，启动时预加载） */
    public static final String MAX_TURNS = "max_turns";
    /** 已用题目 ID 列表（List<Long>，APPEND，用于跟踪已用题目防止重复使用） */
    public static final String USED_QUESTION_IDS = "used_question_ids";
    /** 面试类型（String：TECH/HR，启动时预加载） */
    public static final String INTERVIEW_TYPE = "interview_type";

    /**
     * 状态工厂：创建带 KeyStrategy 注册的 OverAllState。
     * 传给 StateGraph 构造函数。
     */
    public static OverAllStateFactory stateFactory() {
        return () -> newState();
    }

    /**
     * 直接创建一个带全部 KeyStrategy 注册的 OverAllState。
     * 供测试和编排层（预加载数据）使用。
     */
    public static OverAllState newState() {
        OverAllState state = new OverAllState();
        Map<String, KeyStrategy> strategies = new HashMap<>();
        // 默认 REPLACE
        strategies.put(INTERVIEW_ID, new ReplaceStrategy());
        strategies.put(PHASE, new ReplaceStrategy());
        strategies.put(TURN_INDEX, new ReplaceStrategy());
        strategies.put(RESUME_TEXT, new ReplaceStrategy());
        strategies.put(BANK_ID, new ReplaceStrategy());
        strategies.put(CURRENT_QUESTION_ID, new ReplaceStrategy());
        strategies.put(CURRENT_QUESTION, new ReplaceStrategy());
        strategies.put(AI_OUTPUT, new ReplaceStrategy());
        strategies.put(USER_ANSWER, new ReplaceStrategy());
        // 列表追加
        strategies.put(MESSAGES, new AppendStrategy());
        strategies.put(JUDGEMENTS, new AppendStrategy());
        strategies.put(SCORES, new AppendStrategy());
        // 汇总
        strategies.put(TOTAL_SCORE, new ReplaceStrategy());
        strategies.put(REPORT, new ReplaceStrategy());
        strategies.put(ERROR, new ReplaceStrategy());
        // 编排辅助（启动时预加载 / 节点间传递）
        strategies.put(MODEL_CONFIG_ID, new ReplaceStrategy());
        strategies.put(POSITION, new ReplaceStrategy());
        strategies.put(LEVEL, new ReplaceStrategy());
        strategies.put(SKILL_PROMPT, new ReplaceStrategy());
        strategies.put(QUESTIONS, new ReplaceStrategy());
        strategies.put(SCORING_POINTS, new ReplaceStrategy());
        strategies.put(STANDARD_ANSWER, new ReplaceStrategy());
        strategies.put(HISTORY, new ReplaceStrategy());
        strategies.put(LAST_JUDGEMENT, new ReplaceStrategy());
        strategies.put(MAX_TURNS, new ReplaceStrategy());
        strategies.put(USED_QUESTION_IDS, new AppendStrategy());
        strategies.put(INTERVIEW_TYPE, new ReplaceStrategy());
        state.registerKeyAndStrategy(strategies);
        return state;
    }
}