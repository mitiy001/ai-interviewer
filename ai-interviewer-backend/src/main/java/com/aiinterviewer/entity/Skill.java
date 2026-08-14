package com.aiinterviewer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("skill")
public class Skill {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String position;
    /** 工程师等级：junior/mid/senior */
    private String level;
    /** 技能类型：TECH（技术面）/ HR（人事面），默认 TECH */
    private String type;
    private String promptTemplate;
    private String scoringDimensions;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
