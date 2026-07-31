package com.aiinterviewer.parser;

import lombok.Data;

import java.util.List;

/**
 * 解析后的题目（题库上传中间产物）
 */
@Data
public class ParsedQuestion {

    private String type;
    private Integer difficulty;
    private String content;
    private String standardAnswer;
    private List<String> scoringPoints;
}
