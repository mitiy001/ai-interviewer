package com.aiinterviewer.graph;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.common.UserContext;
import com.aiinterviewer.entity.AnswerRecord;
import com.aiinterviewer.entity.InterviewRecord;
import com.aiinterviewer.entity.Question;
import com.aiinterviewer.entity.Resume;
import com.aiinterviewer.entity.Skill;
import com.aiinterviewer.exception.BusinessException;
import com.aiinterviewer.graph.nodes.JudgeNode;
import com.aiinterviewer.graph.nodes.OpeningNode;
import com.aiinterviewer.graph.nodes.QuestionNode;
import com.aiinterviewer.graph.nodes.SummaryNode;
import com.aiinterviewer.graph.state.InterviewState;
import com.aiinterviewer.mapper.AnswerRecordMapper;
import com.aiinterviewer.mapper.InterviewRecordMapper;
import com.aiinterviewer.mapper.QuestionMapper;
import com.aiinterviewer.mapper.ResumeMapper;
import com.aiinterviewer.mapper.SkillMapper;
import com.aiinterviewer.dto.resp.InterviewResumeResp;
import com.aiinterviewer.service.ReportService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewExecutor {

    public static final String EV_PHASE = "phase";
    public static final String EV_AI = "ai";
    public static final String EV_WAIT_ANSWER = "wait_answer";
    public static final String EV_JUDGE = "judge";
    public static final String EV_DONE = "done";
    public static final String EV_ERROR = "error";

    private static final long ANSWER_TIMEOUT_MINUTES = 10;
    private static final long SSE_TIMEOUT_MS = 2 * 60 * 60 * 1000L;

    private final OpeningNode openingNode;
    private final QuestionNode questionNode;
    private final JudgeNode judgeNode;
    private final SummaryNode summaryNode;
    private final InterviewRecordMapper interviewRecordMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final ResumeMapper resumeMapper;
    private final QuestionMapper questionMapper;
    private final SkillMapper skillMapper;
    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    private final Map<Long, InterviewSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService workerPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "interview-executor");
        t.setDaemon(true);
        return t;
    });

    public SseEmitter startStream(Long interviewId) {
        InterviewRecord record = mustLoadRunning(interviewId);
        InterviewSession prev = sessions.get(interviewId);
        if (prev != null) {
            log.info("发现面试 {} 的旧会话，关闭后重建", interviewId);
            prev.active = false;
            if (prev.emitter != null) {
                try { prev.emitter.complete(); } catch (Exception ignored) {}
            }
            sessions.remove(interviewId, prev);
        }

        InterviewSession session = new InterviewSession();
        sessions.put(interviewId, session);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        session.emitter = emitter;
        emitter.onCompletion(() -> cleanup(interviewId, session));
        emitter.onTimeout(() -> {
            log.info("SSE 超时 interviewId={}", interviewId);
            session.active = false;
            cleanup(interviewId, session);
        });
        emitter.onError(e -> {
            log.warn("SSE 错误 interviewId={}: {}", interviewId, e.getMessage());
            session.active = false;
            cleanup(interviewId, session);
        });

        String savedContext = record.getContext();
        if (savedContext != null && !savedContext.isEmpty()) {
            log.info("面试 {} 有保存的状态，从断点恢复", interviewId);
            workerPool.submit(() -> runLoopWithRecovery(session, record));
        } else {
            workerPool.submit(() -> runLoop(session, record));
        }
        return emitter;
    }

    public void submitAnswer(Long interviewId, String answer) {
        InterviewSession session = sessions.get(interviewId);
        if (session == null || !session.active) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "当前面试未在等待回答，或会话已结束");
        }
        session.answerQueue.offer(answer);
    }

    public boolean isSessionActive(Long interviewId) {
        InterviewSession session = sessions.get(interviewId);
        return session != null && session.active;
    }

    public void abort(Long interviewId) {
        InterviewSession session = sessions.get(interviewId);
        if (session != null) {
            session.active = false;
        }
        markStatus(interviewId, "ABORTED", null);
        log.info("中断面试 interviewId={} sessionExists={}", interviewId, session != null);
    }

    public InterviewResumeResp buildResumeResp(Long interviewId) {
        InterviewRecord record = interviewRecordMapper.selectById(interviewId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "面试记录不存在");
        }

        InterviewResumeResp resp = new InterviewResumeResp();
        resp.setInterviewId(record.getId());
        resp.setStatus(record.getStatus());
        resp.setMaxTurns(record.getMaxTurns());

        String context = record.getContext();
        if (context == null || context.isEmpty()) {
            resp.setPhase("IDLE");
            resp.setTurnIndex(0);
            resp.setWaitingAnswer(false);
            resp.setMessages(new ArrayList<>());
            return resp;
        }

        try {
            Map<String, Object> ctx = objectMapper.readValue(context,
                    new TypeReference<Map<String, Object>>() {});
            resp.setPhase(str(ctx, "phase", "IDLE"));
            resp.setTurnIndex(intVal(ctx, "turn_index", 0));
            resp.setWaitingAnswer("WAIT_ANSWER".equals(resp.getPhase()));
            resp.setCurrentQuestion(str(ctx, "current_question", ""));

            List<InterviewResumeResp.MessageItem> messages = buildMessagesFromRecords(interviewId, ctx);
            resp.setMessages(messages);
        } catch (Exception e) {
            log.warn("解析面试状态上下文失败 interviewId={}: {}", interviewId, e.getMessage());
            resp.setPhase("IDLE");
            resp.setTurnIndex(0);
            resp.setWaitingAnswer(false);
            resp.setMessages(new ArrayList<>());
        }

        return resp;
    }

    private void runLoop(InterviewSession session, InterviewRecord record) {
        Long interviewId = record.getId();
        SseEmitter emitter = session.emitter;
        try {
            OverAllState state = buildInitialState(record);
            int maxTurns = record.getMaxTurns() == null ? 5 : record.getMaxTurns();

            sendHeartbeat(emitter, session);
            sendEvent(emitter, session, EV_PHASE, Map.of("phase", "OPENING"));
            Map<String, Object> r = openingNode.apply(state);
            state.input(r);
            sendEvent(emitter, session, EV_AI, Map.of(
                    "content", text(r, InterviewState.AI_OUTPUT),
                    "role", "ai"));

            runQuestionLoop(session, state, emitter, interviewId, maxTurns);

            if (!session.active) {
                state.input(Map.of(InterviewState.PHASE, "DISCONNECTED"));
                saveContext(interviewId, state);
                return;
            }

            runSummary(session, state, emitter, interviewId, maxTurns);
        } catch (Exception e) {
            log.error("面试执行失败 interviewId={}", interviewId, e);
            sendEventSafe(emitter, EV_ERROR, Map.of("message", "面试执行失败: " + e.getMessage()));
            markStatus(interviewId, "ABORTED", null);
            try { emitter.complete(); } catch (Exception ignored) {}
        } finally {
            sessions.remove(interviewId, session);
        }
    }

    private void runLoopWithRecovery(InterviewSession session, InterviewRecord record) {
        Long interviewId = record.getId();
        SseEmitter emitter = session.emitter;
        try {
            OverAllState state = rebuildState(record);
            int maxTurns = record.getMaxTurns() == null ? 5 : record.getMaxTurns();
            int currentTurn = state.value(InterviewState.TURN_INDEX, 0);
            String savedPhase = state.value(InterviewState.PHASE, "");

            log.info("重连恢复面试 {} 当前轮次={}/{} 阶段={}", interviewId, currentTurn, maxTurns, savedPhase);

            sendEvent(emitter, session, EV_PHASE, Map.of("phase", "RECOVERING"));

            if (currentTurn >= maxTurns) {
                runSummary(session, state, emitter, interviewId, maxTurns);
                return;
            }

            if ("WAIT_ANSWER".equals(savedPhase)) {
                String currentQuestion = state.value(InterviewState.CURRENT_QUESTION, "");
                if (!currentQuestion.isEmpty()) {
                    log.info("重连恢复：重新发送当前问题 turn={}", currentTurn);
                    sendEvent(emitter, session, EV_AI, Map.of(
                            "content", currentQuestion,
                            "role", "ai", "turn", currentTurn));
                    sendEvent(emitter, session, EV_WAIT_ANSWER, Map.of(
                            "turn", currentTurn,
                            "questionId", state.value(InterviewState.CURRENT_QUESTION_ID, 0L)));

                    String answer = waitForAnswer(session);
                    if (!session.active) {
                        state.input(Map.of(InterviewState.PHASE, "DISCONNECTED"));
                        saveContext(interviewId, state);
                        return;
                    }
                    state.input(Map.of(InterviewState.USER_ANSWER, answer));

                    sendHeartbeat(emitter, session);
                    sendEvent(emitter, session, EV_PHASE, Map.of("phase", "JUDGE"));
                    Map<String, Object> r = judgeNode.apply(state);
                    state.input(r);
                    int lastScore = toInt(r.get(InterviewState.SCORES));
                    String reason = text(r, InterviewState.AI_OUTPUT);
                    sendEvent(emitter, session, EV_JUDGE, Map.of(
                            "turn", currentTurn, "score", lastScore, "reason", reason));
                    sendEvent(emitter, session, EV_AI, Map.of(
                            "content", reason, "role", "ai"));

                    state.input(Map.of(InterviewState.PHASE, "JUDGE"));
                    saveContext(interviewId, state);

                    currentTurn = state.value(InterviewState.TURN_INDEX, currentTurn + 1);
                }
            }

            runQuestionLoop(session, state, emitter, interviewId, maxTurns);

            if (!session.active) {
                state.input(Map.of(InterviewState.PHASE, "DISCONNECTED"));
                saveContext(interviewId, state);
                return;
            }

            runSummary(session, state, emitter, interviewId, maxTurns);
        } catch (Exception e) {
            log.error("重连恢复面试失败 interviewId={}", interviewId, e);
            sendEventSafe(emitter, EV_ERROR, Map.of("message", "面试恢复失败: " + e.getMessage()));
            markStatus(interviewId, "ABORTED", null);
            try { emitter.complete(); } catch (Exception ignored) {}
        } finally {
            sessions.remove(interviewId, session);
        }
    }

    private void runQuestionLoop(InterviewSession session, OverAllState state,
                                  SseEmitter emitter, Long interviewId, int maxTurns) {
        while (session.active) {
            int turn = state.value(InterviewState.TURN_INDEX, 0);
            if (turn >= maxTurns) break;

            sendHeartbeat(emitter, session);
            sendEvent(emitter, session, EV_PHASE, Map.of("phase", "QUESTION"));
            Map<String, Object> r = questionNode.apply(state);
            state.input(r);
            int currentTurn = state.value(InterviewState.TURN_INDEX, turn);
            Long qid = state.value(InterviewState.CURRENT_QUESTION_ID, (Long) null);
            sendEvent(emitter, session, EV_AI, Map.of(
                    "content", text(r, InterviewState.AI_OUTPUT),
                    "role", "ai", "turn", currentTurn));
            sendEvent(emitter, session, EV_WAIT_ANSWER, Map.of(
                    "turn", currentTurn, "questionId", qid == null ? 0 : qid));

            state.input(Map.of(InterviewState.PHASE, "WAIT_ANSWER"));
            saveContext(interviewId, state);

            String answer = waitForAnswer(session);
            if (!session.active) break;
            state.input(Map.of(InterviewState.USER_ANSWER, answer));

            sendHeartbeat(emitter, session);
            sendEvent(emitter, session, EV_PHASE, Map.of("phase", "JUDGE"));
            r = judgeNode.apply(state);
            state.input(r);
            int lastScore = toInt(r.get(InterviewState.SCORES));
            String reason = text(r, InterviewState.AI_OUTPUT);
            sendEvent(emitter, session, EV_JUDGE, Map.of(
                    "turn", currentTurn, "score", lastScore, "reason", reason));
            sendEvent(emitter, session, EV_AI, Map.of(
                    "content", reason, "role", "ai"));

            state.input(Map.of(InterviewState.PHASE, "JUDGE"));
            saveContext(interviewId, state);
        }
    }

    private void runSummary(InterviewSession session, OverAllState state,
                             SseEmitter emitter, Long interviewId, int maxTurns) {
        sendHeartbeat(emitter, session);
        sendEvent(emitter, session, EV_PHASE, Map.of("phase", "SUMMARY"));
        Map<String, Object> r = summaryNode.apply(state);
        state.input(r);
        int totalScore = state.value(InterviewState.TOTAL_SCORE, 0);
        String report = text(r, InterviewState.REPORT);
        sendEvent(emitter, session, EV_AI, Map.of(
                "content", text(r, InterviewState.AI_OUTPUT), "role", "ai"));
        sendEvent(emitter, session, EV_DONE, Map.of(
                "totalScore", totalScore, "report", report));

        try {
            reportService.save(interviewId, totalScore, report);
        } catch (Exception e) {
            log.warn("报告持久化失败 interviewId={}: {}", interviewId, e.getMessage());
        }

        markStatus(interviewId, "FINISHED", totalScore);
        clearContext(interviewId);
        try { emitter.complete(); } catch (Exception ignored) {}
    }

    private static final long HEARTBEAT_INTERVAL_MS = 15_000;

    private void saveContext(Long interviewId, OverAllState state) {
        try {
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("turn_index", state.value(InterviewState.TURN_INDEX, 0));
            ctx.put("max_turns", state.value(InterviewState.MAX_TURNS, 5));
            ctx.put("phase", state.value(InterviewState.PHASE, ""));
            ctx.put("current_question_id", state.value(InterviewState.CURRENT_QUESTION_ID, (Long) null));
            ctx.put("current_question", state.value(InterviewState.CURRENT_QUESTION, ""));
            ctx.put("standard_answer", state.value(InterviewState.STANDARD_ANSWER, ""));
            ctx.put("scoring_points", state.value(InterviewState.SCORING_POINTS, ""));
            ctx.put("history", state.value(InterviewState.HISTORY, ""));
            ctx.put("messages", state.value(InterviewState.MESSAGES, List.of()));
            ctx.put("judgements", state.value(InterviewState.JUDGEMENTS, List.of()));
            ctx.put("scores", state.value(InterviewState.SCORES, List.of()));
            ctx.put("last_judgement", state.value(InterviewState.LAST_JUDGEMENT, ""));
            ctx.put("ai_output", state.value(InterviewState.AI_OUTPUT, ""));
            ctx.put("used_question_ids", state.value(InterviewState.USED_QUESTION_IDS, List.of()));
            ctx.put("interview_type", state.value(InterviewState.INTERVIEW_TYPE, "TECH"));

            String json = objectMapper.writeValueAsString(ctx);
            InterviewRecord upd = new InterviewRecord();
            upd.setId(interviewId);
            upd.setContext(json);
            interviewRecordMapper.updateById(upd);
            log.debug("保存面试状态 interviewId={} turn={}", interviewId, ctx.get("turn_index"));
        } catch (Exception e) {
            log.warn("保存面试状态失败 interviewId={}: {}", interviewId, e.getMessage());
        }
    }

    private void clearContext(Long interviewId) {
        try {
            InterviewRecord upd = new InterviewRecord();
            upd.setId(interviewId);
            upd.setContext(null);
            interviewRecordMapper.updateById(upd);
        } catch (Exception e) {
            log.warn("清除面试状态失败 interviewId={}", interviewId, e.getMessage());
        }
    }

    private OverAllState rebuildState(InterviewRecord record) {
        String context = record.getContext();
        if (context == null || context.isEmpty()) {
            return buildInitialState(record);
        }

        try {
            Map<String, Object> ctx = objectMapper.readValue(context,
                    new TypeReference<Map<String, Object>>() {});

            OverAllState state = buildInitialState(record);

            if (ctx.containsKey("turn_index")) {
                state.input(Map.of(InterviewState.TURN_INDEX, ctx.get("turn_index")));
            }
            if (ctx.containsKey("current_question_id")) {
                state.input(Map.of(InterviewState.CURRENT_QUESTION_ID, ctx.get("current_question_id")));
            }
            if (ctx.containsKey("current_question")) {
                state.input(Map.of(InterviewState.CURRENT_QUESTION, str(ctx, "current_question", "")));
            }
            if (ctx.containsKey("standard_answer")) {
                state.input(Map.of(InterviewState.STANDARD_ANSWER, str(ctx, "standard_answer", "")));
            }
            if (ctx.containsKey("scoring_points")) {
                state.input(Map.of(InterviewState.SCORING_POINTS, str(ctx, "scoring_points", "")));
            }
            if (ctx.containsKey("history")) {
                state.input(Map.of(InterviewState.HISTORY, str(ctx, "history", "")));
            }
            if (ctx.containsKey("messages")) {
                @SuppressWarnings("unchecked")
                List<String> msgs = (List<String>) ctx.get("messages");
                if (msgs != null) {
                    for (String msg : msgs) {
                        state.input(Map.of(InterviewState.MESSAGES, msg));
                    }
                }
            }
            if (ctx.containsKey("judgements")) {
                @SuppressWarnings("unchecked")
                List<String> jdgs = (List<String>) ctx.get("judgements");
                if (jdgs != null) {
                    for (String j : jdgs) {
                        state.input(Map.of(InterviewState.JUDGEMENTS, j));
                    }
                }
            }
            if (ctx.containsKey("scores")) {
                @SuppressWarnings("unchecked")
                List<Integer> scs = (List<Integer>) ctx.get("scores");
                if (scs != null) {
                    for (Integer s : scs) {
                        state.input(Map.of(InterviewState.SCORES, s));
                    }
                }
            }
            if (ctx.containsKey("last_judgement")) {
                state.input(Map.of(InterviewState.LAST_JUDGEMENT, str(ctx, "last_judgement", "")));
            }
            if (ctx.containsKey("ai_output")) {
                state.input(Map.of(InterviewState.AI_OUTPUT, str(ctx, "ai_output", "")));
            }
            if (ctx.containsKey("used_question_ids")) {
                @SuppressWarnings("unchecked")
                List<Object> usedIds = (List<Object>) ctx.get("used_question_ids");
                if (usedIds != null) {
                    for (Object id : usedIds) {
                        if (id instanceof Number n) {
                            state.input(Map.of(InterviewState.USED_QUESTION_IDS, n.longValue()));
                        }
                    }
                }
            }
            if (ctx.containsKey("interview_type")) {
                state.input(Map.of(InterviewState.INTERVIEW_TYPE, str(ctx, "interview_type", "TECH")));
            }

            return state;
        } catch (Exception e) {
            log.warn("重建面试状态失败 interviewId={}: {}", record.getId(), e.getMessage());
            return buildInitialState(record);
        }
    }

    private List<InterviewResumeResp.MessageItem> buildMessagesFromRecords(
            Long interviewId, Map<String, Object> ctx) {
        List<InterviewResumeResp.MessageItem> messages = new ArrayList<>();

        List<AnswerRecord> answers = answerRecordMapper.selectList(
                new LambdaQueryWrapper<AnswerRecord>()
                        .eq(AnswerRecord::getInterviewId, interviewId)
                        .orderByAsc(AnswerRecord::getTurnIndex));

        for (AnswerRecord ar : answers) {
            if (ar.getAiQuestion() != null) {
                InterviewResumeResp.MessageItem aiMsg = new InterviewResumeResp.MessageItem();
                aiMsg.setRole("ai");
                aiMsg.setContent(ar.getAiQuestion());
                aiMsg.setTurn(ar.getTurnIndex());
                messages.add(aiMsg);
            }
            if (ar.getUserAnswer() != null) {
                InterviewResumeResp.MessageItem userMsg = new InterviewResumeResp.MessageItem();
                userMsg.setRole("user");
                userMsg.setContent(ar.getUserAnswer());
                userMsg.setTurn(ar.getTurnIndex());
                messages.add(userMsg);
            }
            if (ar.getScore() != null) {
                InterviewResumeResp.MessageItem scoreMsg = new InterviewResumeResp.MessageItem();
                scoreMsg.setRole("system");
                scoreMsg.setContent(String.format("第 %d 轮评分：%d 分", ar.getTurnIndex() + 1, ar.getScore()));
                scoreMsg.setScore(ar.getScore());
                scoreMsg.setJudgeReason(ar.getJudgeReason());
                messages.add(scoreMsg);
            }
        }

        if (ctx != null && "WAIT_ANSWER".equals(str(ctx, "phase", ""))) {
            String currentQ = str(ctx, "current_question", "");
            int turnIdx = intVal(ctx, "turn_index", 0);
            if (!currentQ.isEmpty()) {
                boolean exists = messages.stream().anyMatch(m ->
                        "ai".equals(m.getRole()) && turnIdx == m.getTurn());
                if (!exists) {
                    InterviewResumeResp.MessageItem aiMsg = new InterviewResumeResp.MessageItem();
                    aiMsg.setRole("ai");
                    aiMsg.setContent(currentQ);
                    aiMsg.setTurn(turnIdx);
                    messages.add(aiMsg);
                }
            }
        }

        return messages;
    }

    private String waitForAnswer(InterviewSession session) {
        session.answerQueue.clear();
        String answer = null;
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ANSWER_TIMEOUT_MINUTES);
        long lastHeartbeat = System.currentTimeMillis();
        while (session.active && answer == null && System.currentTimeMillis() < deadline) {
            try {
                answer = session.answerQueue.poll(500, TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();
                if (answer == null && now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
                    lastHeartbeat = now;
                    sendEventSafe(session.emitter, "heartbeat", Map.of("ts", now));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (answer == null) {
            log.warn("等待回答超时，按未作答处理");
            answer = "";
        }
        return answer;
    }

    private OverAllState buildInitialState(InterviewRecord record) {
        OverAllState state = InterviewState.newState();

        Resume resume = resumeMapper.selectById(record.getResumeId());
        String resumeText = resume != null && resume.getParsedText() != null
                ? resume.getParsedText() : "";

        List<Question> questions = questionMapper.selectList(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getBankId, record.getBankId()));
        String questionsJson = buildQuestionsJson(questions);

        Skill skill = skillMapper.selectById(record.getSkillId());
        String skillPrompt = skill != null && skill.getPromptTemplate() != null
                ? skill.getPromptTemplate() : "";
        String position = skill != null && skill.getPosition() != null
                ? skill.getPosition() : "Java";
        String level = skill != null && skill.getLevel() != null
                ? skill.getLevel() : "mid";
        String interviewType = record.getInterviewType() != null
                ? record.getInterviewType() : "TECH";

        Map<String, Object> initial = new HashMap<>();
        initial.put(InterviewState.INTERVIEW_ID, record.getId());
        initial.put(InterviewState.MODEL_CONFIG_ID, record.getModelConfigId());
        initial.put(InterviewState.RESUME_TEXT, resumeText);
        initial.put(InterviewState.QUESTIONS, questionsJson);
        initial.put(InterviewState.SKILL_PROMPT, skillPrompt);
        initial.put(InterviewState.POSITION, position);
        initial.put(InterviewState.LEVEL, level);
        initial.put(InterviewState.INTERVIEW_TYPE, interviewType);
        initial.put(InterviewState.TURN_INDEX, 0);
        initial.put(InterviewState.MAX_TURNS, record.getMaxTurns() == null ? 5 : record.getMaxTurns());
        state.input(initial);
        return state;
    }

    private String buildQuestionsJson(List<Question> questions) {
        ArrayNode arr = objectMapper.createArrayNode();
        if (questions != null) {
            for (Question q : questions) {
                ObjectNode qn = objectMapper.createObjectNode();
                qn.put("id", q.getId());
                qn.put("content", q.getContent() == null ? "" : q.getContent());
                qn.put("standard_answer", q.getStandardAnswer() == null ? "" : q.getStandardAnswer());
                qn.put("scoring_points", q.getScoringPoints() == null ? "" : q.getScoringPoints());
                arr.add(qn);
            }
        }
        return arr.toString();
    }

    private InterviewRecord mustLoadRunning(Long interviewId) {
        InterviewRecord record = interviewRecordMapper.selectById(interviewId);
        if (record == null || !UserContext.getUserId().equals(record.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "面试记录不存在");
        }
        if (!"RUNNING".equals(record.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "面试已结束（" + record.getStatus() + "），无法再次连接");
        }
        return record;
    }

    private void markStatus(Long interviewId, String status, Integer totalScore) {
        try {
            InterviewRecord upd = new InterviewRecord();
            upd.setId(interviewId);
            upd.setStatus(status);
            if (totalScore != null) {
                upd.setTotalScore(totalScore);
            }
            if ("FINISHED".equals(status) || "ABORTED".equals(status)) {
                upd.setEndTime(LocalDateTime.now());
            }
            interviewRecordMapper.updateById(upd);
            log.info("面试状态更新 interviewId={} status={} totalScore={}",
                    interviewId, status, totalScore);
        } catch (Exception e) {
            log.error("更新面试状态失败 interviewId={}", interviewId, e);
        }
    }

    private void cleanup(Long interviewId, InterviewSession session) {
        session.active = false;
        sessions.remove(interviewId, session);
    }

    private void sendEvent(SseEmitter emitter, InterviewSession session, String name, Object data) {
        if (!session.active) return;
        sendEventSafe(emitter, name, data);
    }

    private void sendEventSafe(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(name)
                    .data(objectMapper.writeValueAsString(data)));
        } catch (Exception e) {
            log.debug("SSE 发送失败 event={}: {}", name, e.getMessage());
        }
    }

    private void sendHeartbeat(SseEmitter emitter, InterviewSession session) {
        if (!session.active) return;
        sendEventSafe(emitter, "heartbeat", Map.of("ts", System.currentTimeMillis()));
    }

    private static String text(Map<String, Object> result, String key) {
        Object v = result.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    private static String str(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static int intVal(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    private static class InterviewSession {
        volatile SseEmitter emitter;
        final java.util.concurrent.LinkedBlockingQueue<String> answerQueue =
                new java.util.concurrent.LinkedBlockingQueue<>();
        volatile boolean active = true;
    }
}
