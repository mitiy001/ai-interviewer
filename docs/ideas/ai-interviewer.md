# AI 面试官 (AI Interviewer)

## Problem Statement
如何构建一个让用户上传简历/题库/面经后、由 Spring AI Alibaba graph 驱动 AI 进行自适应追问面试、并产出可重练知识图谱报告的、可演进到多用户的 AI 面试官？

## Recommended Direction
方向 C 骨架 + A 作为 Phase 2 第一个差异化特性。MVP 先用文本流 + 单面试官 + 简历/题库输入 + graph 线性流程（开场→提问→判定→总结）+ 基础评分报告，把端到端承重墙跑通；架构上预留多租户、Skill 插件、graph 子图重跑三个扩展点，对应未来多人用、岗位切换、图谱报告重练。

Phase 2 立刻上 adaptive probing（基于判定结果的条件分支 + 置信度信号驱动的追问/升级），这是和"念题机"型 AI 面试官的根本区别，也是产品护城河。Phase 3 上知识图谱报告+重练（输出层留存引擎），Phase 4 上面试官小组 + 面经克隆 + 语音双模态。

理由：你明确要"贴近可上线生产环境 + 未来多人用 + 完整愿景分阶段"。一次性上 adaptive+语音+小组+面经 会让 graph、ASR、判定三重不确定性叠加，出问题无法定位。先通线性链路验证"上传→面试→报告"闭环，再叠差异化，是工程上最稳的路径。MVP 用文本流也顺便砍掉了 WebRTC 这个对"人对 AI"过度工程的复杂度（WebSocket 推音频流才是 Phase 4 语音的真选项）。

## Key Assumptions to Validate
- [ ] 中文技术名词 ASR 准确率 — Phase 4 前用 3 段真实面试录音跑 ASR，"Spring Bean/依赖注入/AOP" 识别率 > 85% 才上语音
- [ ] Spring AI Alibaba graph 条件分支成熟度 — 写 2 节点条件分支 demo，确认支持判定结果驱动的边 + 状态回写（Phase 2 前必须验证）
- [ ] 默认 Java 题库种子数据源 — 确认自建/爬取/开源哪个，MVP 需至少 50 道带标准答案 + 判定要点的题
- [ ] LLM 判 LLM 答的评分稳定性 — 10 题 × 3 正确度答案 × 跑 3 次，评分方差可接受才信判定
- [ ] "TRAE 式 Skill" 落地方式 — 确认是 DB/文件系统里的结构化 prompt 按岗位加载，不是真集成 TRAE
- [ ] 多用户成本悬崖 — MVP 就把"模型可配置"做成成本开关，单次面试成本可估算

## MVP Scope (Phase 1)
In:
- 文本流式对话（SSE/WebSocket，不上 WebRTC）
- 单面试官、单岗位（默认 Java）
- 输入：简历 + 题库上传；面试前检查未上传并提醒
- graph 线性流程：开场 → 按题库提问 → 逐题判定 → 总结
- 简历 × 题库结合：AI 据简历挑选/调整问题
- 基础评分报告（每题分 + 总评 + 改进点）
- 模型 API 可配置（用户填 key/模型名/端点）
- 判定逻辑封装成可切换的 Skill（结构化 prompt，DB 存储）
- MySQL 持久化（用户/题库/简历/面试记录/报告/Skill）
- 架构预留扩展点：多租户 user_id、Skill 插件接口、graph 子图重跑接口

Out（推迟）:
- 语音双模态（Phase 4）
- adaptive probing 追问（Phase 2）
- 面试官小组多角色（Phase 4）
- 面经克隆聚类（Phase 4）
- 知识图谱可视化报告 + 重练（Phase 3）
- 多用户鉴权/并发（Phase 4+，MVP 只预留接口）

## Not Doing (and Why)
- WebRTC（MVP）— 对"人对 AI"语音过度工程，server-side ASR/TTS pipeline 用 WebSocket 更简单。留到 Phase 4 真做语音时再评估。
- 多用户鉴权/多租户实现（MVP）— 你说"未来要多人用"，但 MVP 先预留 user_id 和接口，不实现登录/并发隔离，避免分散精力。
- 面经克隆作为唯一输入（MVP）— 冷启动断崖，用户没面经时产品价值崩。MVP 用简历+题库，面经作为 Phase 4 增强输入。
- 可视化 graph 流编辑器 — graph 在代码里定义为状态机，不做拖拽编辑。前端工作量过大且非核心。
- adaptive probing 放进 MVP — 故意推迟到 Phase 2。MVP 先证明线性链路通，再叠条件分支，否则 graph+判定+追问三重不确定性叠加难调试。
- 面试官小组放进 MVP — 单用户场景下多角色收益边际递减，token 成本 ×3。Phase 4。
- 真集成 TRAE Skill — "TRAE 式"只借用"可复用指令包"模式，后端是 SpringBoot，实际实现成 DB 结构化 prompt 按岗位加载。

## Open Questions
- 默认 Java 题库的种子数据从哪来？（自建 / 开源 / 爬取牛客）
- 模型可配置的粒度：只支持 OpenAI 兼容 API，还是要适配国产模型（通义/智谱/DeepSeek）各自的 SDK？
- "逐题判定"的判定者：用同一个面试 LLM 自判，还是独立 Judge LLM？（成本 vs 一致性）
- 面试记录的隐私边界：未来多人用时，简历/面经是否加密存储？
- 报告格式：Phase 1 给 HTML / PDF / 纯文本哪个？

## Phased Roadmap
- Phase 1 (MVP)：文本 + 单面试官 + 简历/题库 + 线性 graph + 基础报告 + 模型可配置 + Skill 判定
- Phase 2：adaptive probing（条件分支追问）— 第一个差异化
- Phase 3：知识图谱报告 + 可重练子图
- Phase 4：语音双模态 + 面试官小组 + 面经克隆
- Phase 5+：多用户鉴权 / 并发 / 成本治理

## Tech Stack
- 前端：Vue 3
- 后端：Spring Boot + Spring AI Alibaba（graph 编排）
- 数据库：MySQL（root / 123456，已就绪）
- 实时通信：MVP 用 SSE/WebSocket 文本流；Phase 4 语音用 WebSocket 推音频流（不上 WebRTC）
- AI 模型：用户可配置 API（key / 模型名 / 端点）
- 判定 Skill：TRAE 式可复用结构化 prompt，DB 存储，按岗位加载
