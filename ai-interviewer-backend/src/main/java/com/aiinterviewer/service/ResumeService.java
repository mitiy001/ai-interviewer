package com.aiinterviewer.service;

import com.aiinterviewer.dto.resp.ResumeResp;
import com.aiinterviewer.dto.resp.UploadResultResp;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 简历 Service
 */
public interface ResumeService {

    /** 上传并解析简历，返回新简历 ID + 解析长度 */
    UploadResultResp upload(MultipartFile file);

    /** 列出当前用户的简历 */
    List<ResumeResp> list();

    /** 获取单个简历（含解析预览） */
    ResumeResp get(Long id);

    /** 删除简历 */
    void delete(Long id);

    /** 当前用户是否有至少一份简历（供面试启动校验用） */
    boolean exists();
}
