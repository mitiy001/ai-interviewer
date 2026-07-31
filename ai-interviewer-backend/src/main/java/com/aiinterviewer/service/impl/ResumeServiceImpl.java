package com.aiinterviewer.service.impl;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.common.UserContext;
import com.aiinterviewer.dto.resp.ResumeResp;
import com.aiinterviewer.dto.resp.UploadResultResp;
import com.aiinterviewer.entity.Resume;
import com.aiinterviewer.exception.BusinessException;
import com.aiinterviewer.mapper.ResumeMapper;
import com.aiinterviewer.parser.TikaParser;
import com.aiinterviewer.service.ResumeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 简历 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeMapper resumeMapper;
    private final TikaParser tikaParser;

    private static final int PREVIEW_LEN = 200;

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
        String parsedText;
        try (InputStream in = file.getInputStream()) {
            parsedText = tikaParser.parse(filename, in);
        } catch (IOException e) {
            log.error("读取上传文件失败 filename={}", filename, e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "读取文件失败: " + e.getMessage());
        }
        if (parsedText.isBlank()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "文件解析后内容为空");
        }

        Resume resume = new Resume();
        resume.setUserId(UserContext.getUserId());
        resume.setFilename(filename);
        resume.setRawText(parsedText);
        resume.setParsedText(parsedText);
        resume.setUploadedAt(LocalDateTime.now());
        resumeMapper.insert(resume);
        log.info("简历上传成功 id={} filename={} length={}", resume.getId(), filename, parsedText.length());
        return UploadResultResp.ofResume(resume.getId(), parsedText.length());
    }

    @Override
    public List<ResumeResp> list() {
        LambdaQueryWrapper<Resume> qw = new LambdaQueryWrapper<>();
        qw.eq(Resume::getUserId, UserContext.getUserId())
                .orderByDesc(Resume::getUploadedAt);
        return resumeMapper.selectList(qw).stream()
                .map(ResumeServiceImpl::toResp)
                .collect(Collectors.toList());
    }

    @Override
    public ResumeResp get(Long id) {
        Resume entity = mustGetOwned(id);
        ResumeResp resp = toResp(entity);
        // 详情接口额外返回全文，供前端弹窗展示
        resp.setParsedText(entity.getParsedText());
        return resp;
    }

    @Override
    public void delete(Long id) {
        mustGetOwned(id);
        resumeMapper.deleteById(id);
        log.info("删除简历 id={}", id);
    }

    @Override
    public boolean exists() {
        LambdaQueryWrapper<Resume> qw = new LambdaQueryWrapper<>();
        qw.eq(Resume::getUserId, UserContext.getUserId()).last("LIMIT 1");
        return resumeMapper.selectCount(qw) > 0;
    }

    // ---------- private ----------

    private Resume mustGetOwned(Long id) {
        Resume entity = resumeMapper.selectById(id);
        if (entity == null || !UserContext.getUserId().equals(entity.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "简历不存在");
        }
        return entity;
    }

    private static ResumeResp toResp(Resume entity) {
        ResumeResp resp = new ResumeResp();
        resp.setId(entity.getId());
        resp.setUserId(entity.getUserId());
        resp.setFilename(entity.getFilename());
        resp.setUploadedAt(entity.getUploadedAt());
        String text = entity.getParsedText() == null ? "" : entity.getParsedText();
        resp.setParsedLength(text.length());
        resp.setParsedPreview(text.length() <= PREVIEW_LEN ? text : text.substring(0, PREVIEW_LEN) + "...");
        return resp;
    }
}
