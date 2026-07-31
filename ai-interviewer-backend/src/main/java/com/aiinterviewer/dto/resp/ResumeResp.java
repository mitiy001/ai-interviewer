package com.aiinterviewer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历响应
 * <p>
 * 列表接口仅返回 parsedPreview 预览；详情接口（getResume）额外返回 parsedText 全文。
 */
@Data
public class ResumeResp {

    private Long id;
    private Long userId;
    private String filename;
    /** 解析后纯文本前 200 字符预览 */
    private String parsedPreview;
    /** 解析后纯文本总长度 */
    private Integer parsedLength;
    /** 解析后纯文本全文（仅详情接口返回，列表接口为 null 以减少传输） */
    private String parsedText;
    private LocalDateTime uploadedAt;
}
