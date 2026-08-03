package com.aiinterviewer.service;

import com.aiinterviewer.dto.resp.PracticeQuestionResp;

import java.util.List;

/**
 * 错题重练 Service：根据面试中得分较低的题目生成练习题。
 */
public interface PracticeService {

    /**
     * 根据面试记录生成错题重练题目。
     * 取该面试中得分低于 60% 满分的题目作为薄弱点，调用 LLM 生成练习题。
     *
     * @param interviewId     面试记录 ID
     * @param shortAnswerCount 简答题数量
     * @param codeCount        代码题数量
     * @return 练习题列表
     */
    List<PracticeQuestionResp> generate(Long interviewId, int shortAnswerCount, int codeCount);
}
