package com.aiinterviewer.service.impl;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.common.UserContext;
import com.aiinterviewer.dto.resp.QuestionBankResp;
import com.aiinterviewer.dto.resp.UploadResultResp;
import com.aiinterviewer.entity.Question;
import com.aiinterviewer.entity.QuestionBank;
import com.aiinterviewer.exception.BusinessException;
import com.aiinterviewer.mapper.QuestionBankMapper;
import com.aiinterviewer.mapper.QuestionMapper;
import com.aiinterviewer.parser.ParsedQuestion;
import com.aiinterviewer.parser.QuestionBankParser;
import com.aiinterviewer.service.QuestionBankService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 题库 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionBankServiceImpl implements QuestionBankService {

    private final QuestionBankMapper questionBankMapper;
    private final QuestionMapper questionMapper;
    private final QuestionBankParser questionBankParser;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadResultResp upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件名为空");
        }
        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取题库文件失败 filename={}", filename, e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "读取文件失败: " + e.getMessage());
        }

        QuestionBankParser.ParsedBank parsed = questionBankParser.parse(filename, file.getContentType(), text);

        // 1. 插入 question_bank
        QuestionBank bank = new QuestionBank();
        bank.setUserId(UserContext.getUserId());
        bank.setName(parsed.name());
        bank.setSource("user");
        bank.setDescription(parsed.description());
        bank.setCreatedAt(LocalDateTime.now());
        questionBankMapper.insert(bank);

        // 2. 批量插入 question
        for (ParsedQuestion pq : parsed.questions()) {
            Question q = new Question();
            q.setBankId(bank.getId());
            q.setType(pq.getType() == null ? "theory" : pq.getType());
            q.setDifficulty(pq.getDifficulty() == null ? 1 : pq.getDifficulty());
            q.setContent(pq.getContent());
            q.setStandardAnswer(pq.getStandardAnswer());
            q.setScoringPoints(serializePoints(pq.getScoringPoints()));
            q.setCreatedAt(LocalDateTime.now());
            questionMapper.insert(q);
        }
        log.info("题库上传成功 bankId={} name={} questionCount={}", bank.getId(), bank.getName(), parsed.questions().size());
        return UploadResultResp.ofQuestionBank(bank.getId(), parsed.questions().size());
    }

    @Override
    public List<QuestionBankResp> list() {
        LambdaQueryWrapper<QuestionBank> qw = new LambdaQueryWrapper<>();
        qw.eq(QuestionBank::getUserId, UserContext.getUserId())
                .orderByDesc(QuestionBank::getCreatedAt);
        List<QuestionBank> banks = questionBankMapper.selectList(qw);
        return banks.stream()
                .map(b -> toResp(b, countQuestions(b.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public QuestionBankResp get(Long id) {
        QuestionBank entity = mustGetOwned(id);
        return toResp(entity, countQuestions(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        mustGetOwned(id);
        // 先删题目，再删题库
        LambdaQueryWrapper<Question> qq = new LambdaQueryWrapper<>();
        qq.eq(Question::getBankId, id);
        int deleted = questionMapper.delete(qq);
        questionBankMapper.deleteById(id);
        log.info("删除题库 id={} 删除题目数={}", id, deleted);
    }

    @Override
    public boolean exists() {
        LambdaQueryWrapper<QuestionBank> qw = new LambdaQueryWrapper<>();
        qw.eq(QuestionBank::getUserId, UserContext.getUserId()).last("LIMIT 1");
        return questionBankMapper.selectCount(qw) > 0;
    }

    // ---------- private ----------

    private QuestionBank mustGetOwned(Long id) {
        QuestionBank entity = questionBankMapper.selectById(id);
        if (entity == null || !UserContext.getUserId().equals(entity.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题库不存在");
        }
        return entity;
    }

    private int countQuestions(Long bankId) {
        LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<>();
        qw.eq(Question::getBankId, bankId);
        Long count = questionMapper.selectCount(qw);
        return count == null ? 0 : count.intValue();
    }

    private String serializePoints(List<String> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(points);
        } catch (Exception e) {
            log.warn("序列化 scoringPoints 失败: {}", e.getMessage());
            return null;
        }
    }

    private static QuestionBankResp toResp(QuestionBank entity, int questionCount) {
        QuestionBankResp resp = new QuestionBankResp();
        resp.setId(entity.getId());
        resp.setUserId(entity.getUserId());
        resp.setName(entity.getName());
        resp.setSource(entity.getSource());
        resp.setDescription(entity.getDescription());
        resp.setQuestionCount(questionCount);
        resp.setCreatedAt(entity.getCreatedAt());
        return resp;
    }
}