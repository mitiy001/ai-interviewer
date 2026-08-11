package com.aiinterviewer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SkillResp {

    private Long id;
    private String name;
    private String position;
    private String level;
    private String type;
    private String promptTemplate;
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