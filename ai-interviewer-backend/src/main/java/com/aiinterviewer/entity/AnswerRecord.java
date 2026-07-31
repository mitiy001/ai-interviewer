package com.aiinterviewer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("answer_record")
public class AnswerRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long interviewId;
    private Long questionId;
    private Integer turnIndex;
    private String userAnswer;
    private String aiQuestion;
    private Integer score;
    private String judgeReason;
    private LocalDateTime answeredAt;
}
