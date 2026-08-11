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
    /** 面试类型：TECH（技术面）/ HR（人事面），默认 TECH */
    private String interviewType;
    private String status;
    private Integer maxTurns;
    private Integer totalScore;
    /** 面试状态上下文 JSON（用于断线重连恢复） */
    private String context;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
