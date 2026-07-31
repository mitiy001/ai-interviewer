package com.aiinterviewer.dto.resp;

import lombok.Data;

/**
 * 上传结果响应
 */
@Data
public class UploadResultResp {

    /** 新建的简历 ID 或题库 ID */
    private Long id;
    /** 解析出的题目数量（题库上传时有效，简历上传为 null） */
    private Integer questionCount;
    /** 解析后纯文本长度（简历上传时有效，题库上传为 null） */
    private Integer parsedLength;

    public static UploadResultResp ofResume(Long id, int parsedLength) {
        UploadResultResp r = new UploadResultResp();
        r.setId(id);
        r.setParsedLength(parsedLength);
        return r;
    }

    public static UploadResultResp ofQuestionBank(Long id, int questionCount) {
        UploadResultResp r = new UploadResultResp();
        r.setId(id);
        r.setQuestionCount(questionCount);
        return r;
    }
}
