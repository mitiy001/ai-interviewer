package com.aiinterviewer.graph;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.common.UserContext;
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
import com.aiinterviewer.mapper.InterviewRecordMapper;
import com.aiinterviewer.mapper.QuestionMapper;
import com.aiinterviewer.mapper.ResumeMapper;
import com.aiinterviewer.mapper.SkillMapper;
import com.aiinterviewer.service.ReportService;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
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

        InterviewSession session = new InterviewSession();
        InterviewSession prev = sessions.putIfAbsent(interviewId, session);
        if (prev != null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该面试已存在活动 SSE 连接，请勿重复连接");
        }

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

        workerPool.submit(() -> runLoop(session, record));
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

            while (session.active) {
                int turn = state.value(InterviewState.TURN_INDEX, 0);
                if (turn >= maxTurns) break;

                sendHeartbeat(emitter, session);
                sendEvent(emitter, session, EV_PHASE, Map.of("phase", "QUESTION"));
                r = questionNode.apply(state);
                state.input(r);
                int currentTurn = state.value(InterviewState.TURN_INDEX, turn);
                Long qid = state.value(InterviewState.CURRENT_QUESTION_ID, (Long) null);
                sendEvent(emitter, session, EV_AI, Map.of(
                        "content", text(r, InterviewState.AI_OUTPUT),
                        "role", "ai", "turn", currentTurn));
                sendEvent(emitter, session, EV_WAIT_ANSWER, Map.of(
                        "turn", currentTurn, "questionId", qid == null ? 0 : qid));

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
            }

            if (!session.active) {
                markStatus(interviewId, "ABORTED", null);
                return;
            }

            sendHeartbeat(emitter, session);
            sendEvent(emitter, session, EV_PHASE, Map.of("phase", "SUMMARY"));
            r = summaryNode.apply(state);
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
            emitter.complete();
        } catch (Exception e) {
            log.error("面试执行失败 interviewId={}", interviewId, e);
            sendEventSafe(emitter, EV_ERROR, Map.of("message", "面试执行失败: " + e.getMessage()));
            markStatus(interviewId, "ABORTED", null);
            emitter.complete();
        } finally {
            sessions.remove(interviewId, session);
        }
    }

    private static final long HEARTBEAT_INTERVAL_MS = 15_000;

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
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                        .eq(Question::getBankId, record.getBankId())
                        .orderByAsc(Question::getId));
        String questionsJson = buildQuestionsJson(questions);

        Skill skill = skillMapper.selectById(record.getSkillId());
        String skillPrompt = skill != null && skill.getPromptTemplate() != null
                ? skill.getPromptTemplate() : "";
        String position = skill != null && skill.getPosition() != null
                ? skill.getPosition() : "Java";
        String level = skill != null && skill.getLevel() != null
                ? skill.getLevel() : "mid";

        Map<String, Object> initial = new HashMap<>();
        initial.put(InterviewState.INTERVIEW_ID, record.getId());
        initial.put(InterviewState.MODEL_CONFIG_ID, record.getModelConfigId());
        initial.put(InterviewState.RESUME_TEXT, resumeText);
        initial.put(InterviewState.QUESTIONS, questionsJson);
        initial.put(InterviewState.SKILL_PROMPT, skillPrompt);
        initial.put(InterviewState.POSITION, position);
        initial.put(InterviewState.LEVEL, level);
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
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    private static class InterviewSession {
        volatile SseEmitter emitter;
        final java.util.concurrent.LinkedBlockingQueue<String> answerQueue =
                new java.util.concurrent.LinkedBlockingQueue<>();
        volatile boolean active = true;
    }
}