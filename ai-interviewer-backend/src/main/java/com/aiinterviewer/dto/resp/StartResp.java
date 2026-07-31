package com.aiinterviewer.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 启动面试响应
 */
@Data
@AllArgsConstructor
public class StartResp {

    /** 面试记录 ID */
    private Long interviewId;

    /** 提示信息 */
    private String message;
}
