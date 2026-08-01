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
    private String promptTemplate;
    /** 评分维度 */
    private List<ScoringDimensionReq> scoringDimensions;

    @Data
    public static class ScoringDimensionReq {
        private String name;
        private Integer max;
    }
}