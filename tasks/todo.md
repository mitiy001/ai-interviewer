# Todo: AI 面试官 MVP 任务清单

> 配套计划：[plan.md](plan.md)
> 任务状态用 `[x]` / `[ ]` 标记。每个任务有验收标准 + 验证步骤 + 涉及文件。

---

## Wave 1: 地基

- [ ] **T1: 后端项目骨架**
  - Acceptance: SpringBoot 3 + Java 17 项目可启动，健康检查 200，全局异常处理 + Result<T> 就绪
  - Verify: `mvn spring-boot:run` 启动无错；`curl http://localhost:8080/actuator/health` 返回 UP
  - Files: `ai-interviewer-backend/pom.xml`, `AiInterviewerApplication.java`, `common/Result.java`, `common/ResultCode.java`, `exception/GlobalExceptionHandler.java`, `application.yml`

- [ ] **T2: 前端项目骨架**
  - Acceptance: Vue3 + Vite + TS 项目可启动，Pinia + Router + axios 装好，基础布局（顶部导航 + 主内容区）就绪，TRAE 深色主题样式
  - Verify: `pnpm dev` 启动，浏览器访问 5173 看到导航 + 空主内容区
  - Files: `ai-interviewer-frontend/package.json`, `vite.config.ts`, `src/main.ts`, `src/App.vue`, `src/router/index.ts`, `src/api/http.ts`, `src/assets/main.css`

- [ ] **T3: DB schema + seed 建表**
  - Acceptance: schema.sql 顶部 `CREATE DATABASE IF NOT EXISTS ai_interviewer`，9 张表全部建出，外键/索引就绪；seed.sql 插入 1 个默认 user + 1 个默认 Java skill + 30 道种子题
  - Verify: `mysql -uroot -p123456 < ai-interviewer-backend/src/main/resources/db/schema.sql` 成功；`mysql -uroot -p123456 < ai-interviewer-backend/src/main/resources/db/seed.sql` 成功；`SELECT COUNT(*) FROM question` 返回 30
  - Files: `ai-interviewer-backend/src/main/resources/db/schema.sql`, `ai-interviewer-backend/src/main/resources/db/seed.sql`

---

## Wave 2: 后端数据层

- [ ] **T4: 全部 entity + mapper**
  - Acceptance: 9 张表对应 9 个 entity（@TableName + Lombok @Data），9 个 mapper 接口继承 BaseMapper，MyBatis-Plus 配置就绪（驼峰映射、自增主键）
  - Verify: 后端启动无错；写一个临时 `@SpringBootTest` 测试 `baseMapper.selectList(null)` 能查到 seed 数据
  - Files: `entity/User.java` + 8 个其他 entity, `mapper/*.java`（9 个）, `config/MybatisPlusConfig.java`
  - 注：本任务文件数超 5，因为是批量生成，可接受

---

## Wave 3: 后端基础能力（可并行）

- [ ] **T5: 模型配置 CRUD**
  - Acceptance: `GET/POST/PUT/DELETE /api/model-config` 全可用；同一 user 只能有一个 `is_active=true`；api_key 存 DB 不进日志
  - Verify: `mvn test` 跑 MockMvc 集成测试通过；curl 测 CRUD 全流程
  - Files: `controller/ModelConfigController.java`, `service/ModelConfigService.java` + `impl/ModelConfigServiceImpl.java`, `dto/req/ModelConfigReq.java`, `dto/resp/ModelConfigResp.java`

- [ ] **T6: Skill 加载 + 默认 Java Skill**
  - Acceptance: `GET /api/skill` 返回所有 skill；`GET /api/skill/active` 返回当前激活 skill；默认 Java skill 的 prompt 模板 + 评分维度在 seed.sql 里
  - Verify: 启动后 `GET /api/skill` 返回至少 1 个 Java skill，含 prompt_template 和 scoring_dimensions
  - Files: `controller/SkillController.java`, `service/SkillService.java` + `impl/SkillServiceImpl.java`, `dto/resp/SkillResp.java`, `skill/SkillLoader.java`
  - 注：默认 Java skill 的 prompt 内容写到 seed.sql，不在本任务代码里

- [ ] **T7: 文件上传 + Tika 解析**
  - Acceptance: `POST /api/resume/upload`（multipart）支持 PDF/Word/MD/TXT，Tika 解析出纯文本存 `resume.parsed_text`；`POST /api/question-bank/upload` 支持 JSON（结构化）和 MD（每题 `## Q1` 分隔），解析后批量插入 `question` 表
  - Verify: 上传一个 PDF 简历，`SELECT parsed_text FROM resume` 看到纯文本；上传 JSON 题库，`SELECT COUNT(*) FROM question WHERE bank_id=X` 数量正确
  - Files: `controller/UploadController.java`, `service/ResumeService.java` + `impl/ResumeServiceImpl.java`, `service/QuestionBankService.java` + `impl/QuestionBankServiceImpl.java`, `parser/TikaParser.java`, `parser/QuestionBankParser.java`
  - 注：本任务文件数 6，因为是简历+题库两个上传，可接受

---

## Wave 4: 后端 graph 核心（串行）

- [ ] **T8: Spring AI Alibaba graph 配置 + ChatClient 工厂**
  - Acceptance: 引入 `spring-ai-alibaba-graph` 依赖；写一个 `ChatClientFactory` 能根据 `model_config` 动态构造 `OpenAiChatModel`（不依赖 Spring AI 自动配置 bean）；写一个 2 节点条件分支 demo graph 验证 API
  - Verify: 写单元测试 `ChatClientFactoryTest`，mock model_config 构造出 ChatModel 并能调用（用 mock server）；demo graph 能跑通条件分支
  - Files: `pom.xml`（加依赖）, `config/GraphConfig.java`, `graph/ChatClientFactory.java`, `graph/state/InterviewState.java`, `graph/demo/ConditionBranchDemo.java`
  - 风险点：graph API 形态，先写 demo 验证

- [ ] **T9: graph 节点实现**
  - Acceptance: 实现 4 个节点 bean：`OpeningNode`（开场白）、`QuestionNode`（按题库 + 简历挑题提问）、`JudgeNode`（用 Skill 判定用户回答）、`SummaryNode`（总结）；每个节点输入输出 `InterviewState`；prompt 模板从 `resources/prompts/` 加载
  - Verify: 每个节点单测通过（mock ChatClient，验证 prompt 拼接正确、state 更新正确）
  - Files: `graph/nodes/OpeningNode.java`, `graph/nodes/QuestionNode.java`, `graph/nodes/JudgeNode.java`, `graph/nodes/SummaryNode.java`, `resources/prompts/{opening,question,judge,summary}.md`

- [ ] **T10: graph 编排 + 启动校验**
  - Acceptance: `InterviewGraphConfig` 用 graph API 把 4 节点连成线性流程（opening → 循环[question → judge] → summary）；`POST /api/interview/start` 先检查当前 user 是否有 resume 和 question_bank，没有返回 400 + 明确提示，有则创建 `interview_record` 并返回 id
  - Verify: 未上传简历调 start 返回 400 + "请先上传简历"；上传后返回 200 + interview_id；graph 能从 opening 跑到 summary（用 mock LLM）
  - Files: `graph/InterviewGraphConfig.java`, `service/InterviewService.java` + `impl/InterviewServiceImpl.java`（start 方法）, `controller/InterviewController.java`（start 端点）, `dto/req/StartReq.java`, `dto/resp/StartResp.java`

---

## Wave 5: 后端流式 + 报告

- [ ] **T11: SSE 流式面试主循环**
  - Acceptance: `GET /api/interview/{id}/stream` 返回 `SseEmitter`；graph 在独立线程执行，节点产出（AI 提问、AI 判定）通过 emitter 流式推送；用户回答通过 `POST /api/interview/{id}/answer` 提交，graph 阻塞等待回答后继续；面试结束推送 `[DONE]` 事件
  - Verify: curl 连 SSE 端点能看到流式 AI 开场白；POST answer 后能看到流式判定；最终收到 `[DONE]`
  - Files: `service/impl/InterviewServiceImpl.java`（stream + answer 方法）, `controller/InterviewController.java`（stream + answer 端点）, `graph/InterviewExecutor.java`（异步执行 + 阻塞队列协调）, `dto/req/AnswerReq.java`, `common/SseEventType.java`
  - 风险点：graph 同步执行与 SSE 异步推流的协调，用 BlockingQueue 在节点和 controller 间传消息

- [ ] **T12: 报告生成 + 查询接口**
  - Acceptance: 面试结束（收到 `[DONE]`）后自动生成 `interview_report` 记录（总评 + 改进点 JSON）；`GET /api/interview/{id}/report` 返回报告 + 关联的所有 `answer_record`（每题分 + 判定理 + 用户回答 + AI 问题）
  - Verify: 面试结束后 `SELECT * FROM interview_report WHERE interview_id=X` 有记录；`GET /api/interview/{id}/report` 返回完整 JSON
  - Files: `service/ReportService.java` + `impl/ReportServiceImpl.java`, `controller/ReportController.java`, `dto/resp/ReportResp.java`, `dto/resp/AnswerRecordResp.java`, `service/impl/InterviewServiceImpl.java`（结束时触发报告生成）

---

## Wave 6: 前端页面（可部分并行）

- [ ] **T13: 设置页（模型配置）**
  - Acceptance: Settings.vue 列出所有 model_config，能新增/编辑/删除/激活；表单含 name/provider/api_key/model/endpoint；激活时其他自动取消激活；TRAE 深色主题样式
  - Verify: 浏览器进 Settings 页能 CRUD 模型配置；激活一个后刷新仍激活
  - Files: `src/views/Settings.vue`, `src/stores/model.ts`, `src/api/model.ts`, `src/components/ModelConfigForm.vue`

- [ ] **T14: 上传页（简历 + 题库）**
  - Acceptance: Upload.vue 两个区域：简历上传（拖拽 + 点击，支持 PDF/Word/MD/TXT）+ 题库上传（JSON/MD）；上传后显示已上传列表 + 解析状态；面试前未上传时给视觉提示
  - Verify: 上传 PDF 简历后列表出现该项 + 解析成功标记；上传 JSON 题库后显示题目数量
  - Files: `src/views/Upload.vue`, `src/stores/upload.ts`, `src/api/upload.ts`, `src/components/FileUploader.vue`

- [ ] **T15: 面试页（SSE 流式聊天）**
  - Acceptance: Interview.vue 进入时调 start 校验，未上传跳回 Upload；聊天界面：AI 消息（左）+ 用户消息（右）；用 EventSource 连 SSE，流式渲染 AI 文本；用户输入框 + 发送按钮（POST answer）；面试结束显示"查看报告"按钮
  - Verify: 配置好模型 + 上传后进 Interview 页能看到 AI 流式开场白；发回答后看到 AI 流式判定和下一题
  - Files: `src/views/Interview.vue`, `src/stores/interview.ts`, `src/api/interview.ts`, `src/composables/useSSE.ts`, `src/components/MessageItem.vue`

- [ ] **T16: 报告页**
  - Acceptance: Report.vue 显示总评 + 每题卡片（题号 + 问题 + 用户回答 + 分数 + 判定理）+ 改进点列表；TRAE 风格评分条；可返回首页或再开一场
  - Verify: 面试结束后点"查看报告"进 Report 页，所有数据正确渲染
  - Files: `src/views/Report.vue`, `src/api/report.ts`, `src/components/ScoreCard.vue`, `src/components/QuestionCard.vue`

---

## Wave 7: 联调

- [ ] **T17: 端到端联调 + 完善**
  - Acceptance: spec 的 10 条 Success Criteria 全部满足；从配置模型 → 上传 → 面试 → 报告全流程无报错；种子题库质量可用（30 道覆盖 Java 核心：JVM/集合/并发/Spring/MySQL/Redis）
  - Verify: 手动跑完整流程；`mvn test` + `pnpm test` 全绿；检查 spec 10 条逐条对照
  - Files: 视情况修复涉及的文件；可能补充 `seed.sql` 题目质量
