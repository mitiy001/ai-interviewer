package com.aiinterviewer.service.impl;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.common.UserContext;
import com.aiinterviewer.dto.req.StartReq;
import com.aiinterviewer.dto.resp.InterviewListItemResp;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 面试 Service 实现
 */
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

    @Override
    public StartResp start(StartReq req) {
        Long userId = UserContext.getUserId();

        // 1. 校验简历
        Resume resume = resolveResume(userId, req);
        // 2. 校验题库
        QuestionBank bank = resolveBank(userId, req);
        // 3. 校验模型配置
        ModelConfig modelConfig = resolveModelConfig(req);
        // 4. 校验 Skill
        Skill skill = resolveSkill(req);

        // 5. 计算轮次上限
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

        // 6. 创建面试记录
        InterviewRecord record = new InterviewRecord();
        record.setUserId(userId);
        record.setModelConfigId(modelConfig.getId());
        record.setSkillId(skill.getId());
        // 简历可选：未上传时 resumeId 留空，仅依据题库面试
        if (resume != null) {
            record.setResumeId(resume.getId());
        }
        record.setBankId(bank.getId());
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
        // 简历可选：未指定时取最新一份，没有则返回 null（仅依据题库面试）
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
        // 当前仅支持使用已激活的模型配置
        ModelConfig active = modelConfigService.getActiveRaw();
        if (active == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "请先在设置页激活一个模型配置");
        }
        return active;
    }

    private Skill resolveSkill(StartReq req) {
        // 指定 skillId 时使用指定 skill（支持用户选择不同等级），否则取激活的
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
        item.setStartTime(r.getStartTime());
        item.setEndTime(r.getEndTime());
        return item;
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
        // RUNNING 状态处理：
        //   - 会话仍活跃（用户真在面试中）→ 拒绝删除，提示先结束面试
        //   - 会话已断开（中断后状态残留）→ 自动标记 ABORTED 后允许删除
        if ("RUNNING".equals(record.getStatus())) {
            if (interviewExecutor.isSessionActive(interviewId)) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "面试进行中，请先退出面试后再删除");
            }
            // 会话已断开但状态未更新，自动中断兜底
            log.info("RUNNING 状态但会话不活跃，自动标记 ABORTED 后删除 interviewId={}", interviewId);
            interviewExecutor.abort(interviewId);
        }
        // 删除关联数据：答题记录、报告
        answerRecordMapper.delete(new LambdaQueryWrapper<AnswerRecord>()
                .eq(AnswerRecord::getInterviewId, interviewId));
        interviewReportMapper.delete(new LambdaQueryWrapper<InterviewReport>()
                .eq(InterviewReport::getInterviewId, interviewId));
        interviewRecordMapper.deleteById(interviewId);
        log.info("删除面试记录 interviewId={}", interviewId);
    }
}
