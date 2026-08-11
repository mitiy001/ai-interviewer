package com.aiinterviewer.service.impl;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.common.UserContext;
import com.aiinterviewer.common.RateLimiter;
import com.aiinterviewer.dto.req.StartReq;
import com.aiinterviewer.dto.resp.InterviewListItemResp;
import com.aiinterviewer.dto.resp.InterviewResumeResp;
import com.aiinterviewer.dto.resp.StartResp;
import com.aiinterviewer.entity.AnswerRecord;
import com.aiinterviewer.entity.InterviewRecord;
import com.aiinterviewer.entity.InterviewReport;
import com.aiinterviewer.entity.ModelConfig;
import com.aiinterviewer.entity.QuestionBank;
import com.aiinterviewer.entity.Resume;
import com.aiinterviewer.entity.Skill;
import com.aiinterviewer.exception.BusinessException;
import com.aiinterviewer.graph.InterviewExecutor;
import com.aiinterviewer.mapper.AnswerRecordMapper;
import com.aiinterviewer.mapper.InterviewRecordMapper;
import com.aiinterviewer.mapper.InterviewReportMapper;
import com.aiinterviewer.mapper.QuestionBankMapper;
import com.aiinterviewer.mapper.QuestionMapper;
import com.aiinterviewer.mapper.ResumeMapper;
import com.aiinterviewer.service.InterviewService;
import com.aiinterviewer.service.ModelConfigService;
import com.aiinterviewer.service.SkillService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private static final int DEFAULT_MAX_TURNS = 5;

    private final ResumeMapper resumeMapper;
    private final QuestionBankMapper questionBankMapper;
    private final QuestionMapper questionMapper;
    private final InterviewRecordMapper interviewRecordMapper;
    private final AnswerRecordMapper answerRecordMapper;
    private final InterviewReportMapper interviewReportMapper;
    private final ModelConfigService modelConfigService;
    private final SkillService skillService;
    private final InterviewExecutor interviewExecutor;

    /** 回答提交速率限制：每 10 秒最多 5 次 */
    private final RateLimiter answerRateLimiter = new RateLimiter(5, 10_000);

    @PostConstruct
    public void init() {
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limiter-cleanup");
            t.setDaemon(true);
            return t;
        }).scheduleAtFixedRate(answerRateLimiter::cleanUp, 5, 5, TimeUnit.MINUTES);
    }

    @Override
    public StartResp start(StartReq req) {
        Long userId = UserContext.getUserId();
        Resume resume = resolveResume(userId, req);
        QuestionBank bank = resolveBank(userId, req);
        ModelConfig modelConfig = resolveModelConfig(req);
        Skill skill = resolveSkill(req);

        Long questionCount = questionMapper.selectCount(
                new LambdaQueryWrapper<com.aiinterviewer.entity.Question>()
                        .eq(com.aiinterviewer.entity.Question::getBankId, bank.getId()));
        int qCount = questionCount == null ? 0 : questionCount.intValue();
        if (qCount == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "题库中没有题目，请上传有效题库");
        }
        int maxTurns = req != null && req.getMaxTurns() != null && req.getMaxTurns() > 0
                ? req.getMaxTurns() : DEFAULT_MAX_TURNS;
        maxTurns = Math.min(maxTurns, qCount);

        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setModelConfigId(modelConfig.getId());
        record.setSkillId(skill.getId());
        if (resume != null) {
            record.setResumeId(resume.getId());
        }
        record.setBankId(bank.getId());
        record.setInterviewType(req != null && req.getInterviewType() != null
                ? req.getInterviewType() : "TECH");
        record.setStatus("RUNNING");
        record.setMaxTurns(maxTurns);
        record.setTotalScore(0);
        record.setStartTime(LocalDateTime.now());
        interviewRecordMapper.insert(record);

        log.info("面试启动成功 interviewId={} userId={} bankId={} resumeId={} maxTurns={}",
                record.getId(), userId, bank.getId(), resume == null ? null : resume.getId(), maxTurns);

        return new StartResp(record.getId(), "面试已启动，请连接 SSE 端点开始对话");
    }

    private Resume resolveResume(Long userId, StartReq req) {
        if (req != null && req.getResumeId() != null) {
            Resume r = resumeMapper.selectById(req.getResumeId());
            if (r == null || !userId.equals(r.getUserId())) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "指定的简历不存在");
            }
            return r;
        }
        Resume latest = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getUploadedAt)
                .last("LIMIT 1"));
        return latest;
    }

    private QuestionBank resolveBank(Long userId, StartReq req) {
        if (req != null && req.getBankId() != null) {
            QuestionBank b = questionBankMapper.selectById(req.getBankId());
            if (b == null || !userId.equals(b.getUserId())) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "指定的题库不存在");
            }
            return b;
        }
        QuestionBank latest = questionBankMapper.selectOne(new LambdaQueryWrapper<QuestionBank>()
                .eq(QuestionBank::getUserId, userId)
                .orderByDesc(QuestionBank::getCreatedAt)
                .last("LIMIT 1"));
        if (latest == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "请先上传题库后再开始面试");
        }
        return latest;
    }

    private ModelConfig resolveModelConfig(StartReq req) {
        ModelConfig active = modelConfigService.getActiveRaw();
        if (active == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "请先在设置页激活一个模型配置");
        }
        return active;
    }

    private Skill resolveSkill(StartReq req) {
        if (req != null && req.getSkillId() != null) {
            return skillService.getByIdRaw(req.getSkillId());
        }
        Skill active = skillService.getActiveRaw();
        if (active == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "未发现激活的 Skill，请先执行 seed.sql 导入默认 Skill");
        }
        return active;
    }

    @Override
    public SseEmitter stream(Long interviewId) {
        return interviewExecutor.startStream(interviewId);
    }

    @Override
    public void answer(Long interviewId, String answer) {
        Long userId = UserContext.getUserId();
        if (!answerRateLimiter.tryAcquire(userId)) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "提交回答过于频繁，请稍后再试");
        }
        interviewExecutor.submitAnswer(interviewId, answer);
    }

    @Override
    public List<InterviewListItemResp> list() {
        LambdaQueryWrapper<InterviewRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(InterviewRecord::getUserId, UserContext.getUserId())
                .orderByDesc(InterviewRecord::getStartTime);
        return interviewRecordMapper.selectList(qw).stream()
                .map(this::toListItem)
                .collect(Collectors.toList());
    }

    private InterviewListItemResp toListItem(InterviewRecord r) {
        InterviewListItemResp item = new InterviewListItemResp();
        item.setId(r.getId());
        item.setStatus(r.getStatus());
        item.setMaxTurns(r.getMaxTurns());
        item.setTotalScore(r.getTotalScore());
        item.setInterviewType(r.getInterviewType());
        item.setStartTime(r.getStartTime());
        item.setEndTime(r.getEndTime());
        return item;
    }

    @Override
    public InterviewResumeResp resume(Long interviewId) {
        InterviewRecord record = interviewRecordMapper.selectById(interviewId);
        if (record == null || !UserContext.getUserId().equals(record.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "面试记录不存在");
        }
        return interviewExecutor.buildResumeResp(interviewId);
    }

    @Override
    public void abort(Long interviewId) {
        InterviewRecord record = interviewRecordMapper.selectById(interviewId);
        if (record == null || !UserContext.getUserId().equals(record.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "面试记录不存在");
        }
        interviewExecutor.abort(interviewId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long interviewId) {
        InterviewRecord record = interviewRecordMapper.selectById(interviewId);
        if (record == null || !UserContext.getUserId().equals(record.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "面试记录不存在");
        }
        if ("RUNNING".equals(record.getStatus())) {
            if (interviewExecutor.isSessionActive(interviewId)) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "面试进行中，请先退出面试后再删除");
            }
            log.info("RUNNING 状态但会话不活跃，自动标记 ABORTED 后删除 interviewId={}", interviewId);
            interviewExecutor.abort(interviewId);
        }
        answerRecordMapper.delete(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getInterviewId, interviewId));
        interviewReportMapper.delete(new LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getInterviewId, interviewId));
        interviewRecordMapper.deleteById(interviewId);
        log.info("删除面试记录 interviewId={}", interviewId);
    }
}