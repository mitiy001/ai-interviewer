package com.aiinterviewer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("interview_record")
public class InterviewRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long modelConfigId;
    private Long skillId;
    private Long resumeId;
    private Long bankId;
    private String interviewType;
    private String status;
    private Integer maxTurns;
    private Integer totalScore;
    private String context;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}