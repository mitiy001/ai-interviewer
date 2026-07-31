package com.aiinterviewer.dto.resp;

import lombok.Data;

/**
 * 改进建议（结构化）
 */
@Data
public class ImprovementResp {
    /** 问题描述 */
    private String problem;
    /** 学习路径 */
    private String learningPath;
    /** 练习建议 */
    private String practice;
}
