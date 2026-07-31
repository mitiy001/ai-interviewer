package com.aiinterviewer.service;

import com.aiinterviewer.dto.resp.QuestionBankResp;
import com.aiinterviewer.dto.resp.UploadResultResp;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 题库 Service
 */
public interface QuestionBankService {

    /** 上传题库文件（JSON/MD），解析后批量插入题目，返回题库 ID + 题目数 */
    UploadResultResp upload(MultipartFile file);

    /** 列出当前用户的题库（含题目数） */
    List<QuestionBankResp> list();

    /** 获取单个题库（含题目数） */
    QuestionBankResp get(Long id);

    /** 删除题库（及其所有题目） */
    void delete(Long id);

    /** 当前用户是否有至少一个题库（供面试启动校验用） */
    boolean exists();
}
