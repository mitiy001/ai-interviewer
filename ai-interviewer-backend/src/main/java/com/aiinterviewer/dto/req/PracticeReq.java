package com.aiinterviewer.dto.req;

import lombok.Data;

/**
 * 错题重练请求体。
 */
@Data
public class PracticeReq {
    /** 简答题数量 */
    private int shortAnswerCount = 2;
    /** 代码题数量 */
    private int codeCount = 0;
}
