package com.aiinterviewer.dto.req;

import lombok.Data;

import java.util.List;

/**
 * Skill 判定标准创建/更新请求
 */
@Data
public class SkillReq {

    private String name;
    private String position;
    /** 工程师等级：junior/mid/senior */
    private String level;
    /** 技能类型：TECH（技术面）/ HR（人事面） */
    private String type;
    private String promptTemplate;
    /** 评分维度 */
    private List<ScoringDimensionReq> scoringDimensions;

    @Data
    public static class ScoringDimensionReq {
        private String name;
        private Integer max;
    }
}