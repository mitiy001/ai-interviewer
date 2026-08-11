package com.aiinterviewer.graph.state;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.OverAllStateFactory;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.HashMap;
import java.util.Map;

public final class InterviewState {

    private InterviewState() {
    }

    // ===== 状态 key =====

    public static final String INTERVIEW_ID = "interview_id";
    public static final String PHASE = "phase";
    public static final String TURN_INDEX = "turn_index";
    public static final String RESUME_TEXT = "resume_text";
    public static final String BANK_ID = "bank_id";
    public static final String CURRENT_QUESTION_ID = "current_question_id";
    public static final String CURRENT_QUESTION = "current_question";
    public static final String AI_OUTPUT = "ai_output";
    public static final String USER_ANSWER = "user_answer";
    public static final String MESSAGES = "messages";
    public static final String JUDGEMENTS = "judgements";
    public static final String SCORES = "scores";
    public static final String TOTAL_SCORE = "total_score";
    public static final String REPORT = "report";
    public static final String ERROR = "error";

    public static final String MODEL_CONFIG_ID = "model_config_id";
    public static final String POSITION = "position";
    public static final String LEVEL = "level";
    public static final String SKILL_PROMPT = "skill_prompt";
    public static final String QUESTIONS = "questions";
    public static final String SCORING_POINTS = "scoring_points";
    public static final String STANDARD_ANSWER = "standard_answer";
    public static final String HISTORY = "history";
    public static final String LAST_JUDGEMENT = "last_judgement";
    public static final String MAX_TURNS = "max_turns";
    public static final String USED_QUESTION_IDS = "used_question_ids";
    public static final String INTERVIEW_TYPE = "interview_type";

    public static OverAllStateFactory stateFactory() {
        return () -> newState();
    }

    public static OverAllState newState() {
        OverAllState state = new OverAllState();
        Map<String, KeyStrategy> strategies = new HashMap<>();
        strategies.put(INTERVIEW_ID, new ReplaceStrategy());
        strategies.put(PHASE, new ReplaceStrategy());
        strategies.put(TURN_INDEX, new ReplaceStrategy());
        strategies.put(RESUME_TEXT, new ReplaceStrategy());
        strategies.put(BANK_ID, new ReplaceStrategy());
        strategies.put(CURRENT_QUESTION_ID, new ReplaceStrategy());
        strategies.put(CURRENT_QUESTION, new ReplaceStrategy());
        strategies.put(AI_OUTPUT, new ReplaceStrategy());
        strategies.put(USER_ANSWER, new ReplaceStrategy());
        strategies.put(MESSAGES, new AppendStrategy());
        strategies.put(JUDGEMENTS, new AppendStrategy());
        strategies.put(SCORES, new AppendStrategy());
        strategies.put(TOTAL_SCORE, new ReplaceStrategy());
        strategies.put(REPORT, new ReplaceStrategy());
        strategies.put(ERROR, new ReplaceStrategy());
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