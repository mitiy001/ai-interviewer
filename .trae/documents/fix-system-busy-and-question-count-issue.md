# 修复计划：系统繁忙错误 & 题库题目数量不匹配

## 问题概述

### 问题 1：面试页面和报告页面显示"系统繁忙，请稍后重试"

**现象**：访问面试页面和报告页面时，前端显示"系统繁忙，请稍后重试"。

**根因分析**：
- `GlobalExceptionHandler.handleException(Exception)` 捕获所有未处理异常，返回统一提示"系统繁忙，请稍后重试"
- 在 `QuestionBankServiceImpl.countQuestions()` 方法中，`Math.toIntExact(questionMapper.selectCount(qw))` 存在潜在 NPE——`selectCount` 可能返回 `null`，Java 自动拆箱时抛出 `NullPointerException`
- 面试页面的 `loadList()` 同时调用 5 个 API（`InterviewApi.list()`、`ResumeApi.list()`、`QuestionBankApi.list()`、`SkillApi.list()`、`ModelConfigApi.list()`），其中任何一个抛出异常都会导致页面显示错误

### 问题 2：题库上传后题目数量与本地文档不匹配（40 道 vs 3 道）

**现象**：本地文档有 40 道题目，上传后系统只显示 3 道。

**根因分析**：
- `QuestionBankParser.parseMd()` 使用 `"## "` 作为题目的唯一分隔符
- 如果用户文档的结构是 `##` 作为章节标题、`###` 作为题目分隔符（例如：`## 基础题` 下包含多个 `### Q1`、`### Q2`），则解析器会把整个章节当作一道题，导致题目数量大幅减少
- 用户文档中恰好有 3 个 `##` 章节标题，因此只解析出 3 道题

---

## 修改方案

### 修改 1：`QuestionBankParser.java` — 支持 `###` 作为题目分隔符

**文件**：`ai-interviewer-backend/src/main/java/com/aiinterviewer/parser/QuestionBankParser.java`

**改动**：
- 在 `parseMd()` 方法中，将题目分隔条件从 `line.trim().startsWith("## ")` 改为 `line.trim().startsWith("## ") || line.trim().startsWith("### ")`
- 同时更新 `parseMdBlock()` 中的标题跳过逻辑，同样支持 `### `

**原因**：用户文档可能使用 `###` 作为每道题的分隔符，而 `##` 用作章节标题。支持两种格式可以提高兼容性。

### 修改 2：`QuestionBankParser.java` — 增加解析日志

**文件**：同上

**改动**：
- 在 `parseMd()` 方法末尾，增加 `log.info("MD解析完成: 总行数={}, 切块数={}, 有效题目数={}", lines.length, blocks.size(), questions.size())`
- 如果解析出的题目数量与用户预期差异较大，日志可以帮助排查

**原因**：方便后续调试题目数量不匹配的问题。

### 修改 3：`QuestionBankServiceImpl.java` — 修复 `countQuestions()` 的 NPE

**文件**：`ai-interviewer-backend/src/main/java/com/aiinterviewer/service/impl/QuestionBankServiceImpl.java`

**改动**：
- 将 `countQuestions()` 方法中的 `return Math.toIntExact(questionMapper.selectCount(qw))` 改为带 null 检查的版本

```java
private int countQuestions(Long bankId) {
    LambdaQueryWrapper<Question> qw = new LambdaQueryWrapper<>();
    qw.eq(Question::getBankId, bankId);
    Long count = questionMapper.selectCount(qw);
    return count == null ? 0 : count.intValue();
}
```

**原因**：`Math.toIntExact(null)` 会因自动拆箱抛出 `NullPointerException`，导致 `QuestionBankApi.list()` 接口返回"系统繁忙"错误。

### 修改 4：`GlobalExceptionHandler.java` — 增强异常日志

**文件**：`ai-interviewer-backend/src/main/java/com/aiinterviewer/exception/GlobalExceptionHandler.java`

**改动**：
- 在 `handleException(Exception e)` 方法中，增加对 `e.getMessage()` 的详细日志，并添加请求上下文信息

```java
@ExceptionHandler(Exception.class)
public Result<Void> handleException(Exception e) {
    log.error("未处理异常: {}", e.getMessage(), e);
    return Result.fail(ResultCode.SYSTEM_ERROR, "系统繁忙，请稍后重试");
}
```

**原因**：当前虽然 `log.error("系统异常", e)` 已打印堆栈，但日志中缺少请求路径等信息。增加 `e.getMessage()` 使日志更易读。

### 修改 5：`Interview.vue` — 区分"系统繁忙"和具体错误

**文件**：`ai-interviewer-frontend/src/views/Interview.vue`

**改动**：
- 在 `loadList()` 的 catch 块中，如果后端返回的是"系统繁忙"类型错误，提示用户检查服务器状态或刷新重试

**原因**：提供更友好的用户体验，帮助用户理解问题。

---

## 验证步骤

1. **上传测试**：上传一个包含 40 道题目的 Markdown 文档（使用 `### ` 作为题目分隔符），确认系统正确解析出 40 道题
2. **兼容性测试**：上传一个使用 `## ` 作为题目分隔符的文档，确认仍然能正确解析
3. **面试页面测试**：访问面试页面，确认不再显示"系统繁忙"错误
4. **报告页面测试**：访问报告页面，确认不再显示"系统繁忙"错误
5. **日志检查**：查看服务器日志，确认解析日志和异常日志正常输出