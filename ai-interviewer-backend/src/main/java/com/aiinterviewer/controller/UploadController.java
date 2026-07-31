package com.aiinterviewer.controller;

import com.aiinterviewer.common.Result;
import com.aiinterviewer.dto.resp.QuestionBankResp;
import com.aiinterviewer.dto.resp.ResumeResp;
import com.aiinterviewer.dto.resp.UploadResultResp;
import com.aiinterviewer.service.QuestionBankService;
import com.aiinterviewer.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传 REST 接口
 * <p>
 * 简历：
 * <ul>
 *   <li>POST   /api/resume/upload        上传简历（multipart）</li>
 *   <li>GET    /api/resume               简历列表</li>
 *   <li>GET    /api/resume/{id}          简历详情（预览）</li>
 *   <li>DELETE /api/resume/{id}          删除简历</li>
 * </ul>
 * 题库：
 * <ul>
 *   <li>POST   /api/question-bank/upload 上传题库（multipart，JSON/MD）</li>
 *   <li>GET    /api/question-bank        题库列表</li>
 *   <li>GET    /api/question-bank/{id}   题库详情</li>
 *   <li>DELETE /api/question-bank/{id}   删除题库</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class UploadController {

    private final ResumeService resumeService;
    private final QuestionBankService questionBankService;

    // ===== 简历 =====

    @PostMapping("/api/resume/upload")
    public Result<UploadResultResp> uploadResume(@RequestParam("file") MultipartFile file) {
        return Result.ok(resumeService.upload(file));
    }

    @GetMapping("/api/resume")
    public Result<List<ResumeResp>> listResume() {
        return Result.ok(resumeService.list());
    }

    @GetMapping("/api/resume/{id}")
    public Result<ResumeResp> getResume(@PathVariable Long id) {
        return Result.ok(resumeService.get(id));
    }

    @DeleteMapping("/api/resume/{id}")
    public Result<Void> deleteResume(@PathVariable Long id) {
        resumeService.delete(id);
        return Result.ok();
    }

    // ===== 题库 =====

    @PostMapping("/api/question-bank/upload")
    public Result<UploadResultResp> uploadQuestionBank(@RequestParam("file") MultipartFile file) {
        return Result.ok(questionBankService.upload(file));
    }

    @GetMapping("/api/question-bank")
    public Result<List<QuestionBankResp>> listQuestionBank() {
        return Result.ok(questionBankService.list());
    }

    @GetMapping("/api/question-bank/{id}")
    public Result<QuestionBankResp> getQuestionBank(@PathVariable Long id) {
        return Result.ok(questionBankService.get(id));
    }

    @DeleteMapping("/api/question-bank/{id}")
    public Result<Void> deleteQuestionBank(@PathVariable Long id) {
        questionBankService.delete(id);
        return Result.ok();
    }
}
