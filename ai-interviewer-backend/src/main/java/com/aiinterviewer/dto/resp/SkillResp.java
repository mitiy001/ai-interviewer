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
    /** 技能类型：TECH（技术面）/ HR（人事面） */
    private String type;
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
