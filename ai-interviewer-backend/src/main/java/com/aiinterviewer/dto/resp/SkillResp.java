package com.aiinterviewer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Skill 判定标准响应
 */
@Data
public class SkillResp {

    private Long id;
    private String name;
    private String position;
    /** 工程师等级：junior/mid/senior */
    private String level;
    private String promptTemplate;
    /** 评分维度（已解析为结构化对象） */
    private List<ScoringDimension> scoringDimensions;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class ScoringDimension {
        private String name;
        private Integer max;
    }
}
