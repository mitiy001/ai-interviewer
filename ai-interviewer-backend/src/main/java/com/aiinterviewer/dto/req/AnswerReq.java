package com.aiinterviewer.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户提交面试回答请求
 */
@Data
public class AnswerReq {

    @NotBlank(message = "answer 不能为空")
    private String answer;
}
