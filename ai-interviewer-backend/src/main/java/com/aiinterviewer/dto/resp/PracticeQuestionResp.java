package com.aiinterviewer.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 错题重练生成的练习题
 */
@Data
public class PracticeQuestionResp {

    /** 题型：single_choice / short_answer / code */
    private String type;
    /** 题干 */
    private String question;
    /** 选项（单选题有效） */
    private List<String> options;
    /** 参考答案（单选为字母如 "B"，简答为文本，代码为代码内容） */
    private String answer;
    /** 解析 */
    private String explanation;
    /** 考察知识点 */
    private String knowledgePoint;
}
