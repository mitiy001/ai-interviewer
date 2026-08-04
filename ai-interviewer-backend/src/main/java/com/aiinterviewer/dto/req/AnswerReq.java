package com.aiinterviewer.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户提交面试回答请求
 */
@Data
public class AnswerReq {

    @NotBlank(message = "answer 不能为空")
    @Size(max = 20000, message = "回答长度不能超过 20000 字")
    private String answer;
}
