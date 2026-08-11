package com.aiinterviewer.dto.req;

import lombok.Data;

import java.util.List;

@Data
public class SkillReq {

    private String name;
    private String position;
    private String level;
    private String type;
    private String promptTemplate;
    private List<ScoringDimensionReq> scoringDimensions;

    @Data
    public static class ScoringDimensionReq {
        private String name;
        private Integer max;
    }
}