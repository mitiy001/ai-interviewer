# Spec: AI 面试官 (AI Interviewer)

> 配套构思文档：[docs/ideas/ai-interviewer.md](ideas/ai-interviewer.md)
> 本 spec 仅覆盖 **Phase 1 (MVP)**。Phase 2-5 见构思文档 roadmap。

## Objective

构建一个单用户（架构预留多租户）的 AI 面试官，让用户上传简历和题库后，由 Spring AI Alibaba graph 驱动 AI 进行文本流式面试，逐题判定并生成 HTML 报告。

**用户故事：**
- 作为求职者，我想上传简历和 Java 题库，让 AI 据此对我进行模拟面试
- 作为求职者，我想在面试前被提醒"未上传简历/题库就不能开始"
- 作为求职者，我想看到 AI 流式输出问题、能流式回答，像真实对话
- 作为求职者，我想面试结束后拿到每题评分 + 总评 + 改进点的报告
- 作为求职者，我想自己配置模型 API（key/模型名/端点），不绑定厂商

**MVP 不做：** 语音、adaptive 追问、面试官小组、面经克隆、图谱报告、多用户鉴权（见构思文档 Not Doing）。

## Tech Stack

**后端：**
- Java 17
- Spring Boot 3.x
- Spring AI Alibaba（graph 编排，版本跟随官方最新稳定版）
- MyBatis-Plus（ORM）
- MySQL 8.x（root / 123456，已就绪）
- Apache Tika（简历/题库文件解析，全格式）
- Lombok、Hutool（工具库）
- Maven 构建

**前端：**
- Vue 3 + Vite + TypeScript
- Pinia（状态管理）
- Vue Router
- axios（REST）
- EventSource（SSE 流式）
- pnpm 包管理

## Commands

**后端（ai-interviewer-backend/）：**
```bash
# 启动开发（默认 8080 端口）
mvn spring-boot:run

# 打包
mvn clean package -DskipTests

# 跑测试
mvn test

# 初始化数据库（首次）
mysql -uroot -p123456 < src/main/resources/db/schema.sql
mysql -uroot -p123456 < src/main/resources/db/seed.sql
```

**前端（ai-interviewer-frontend/）：**
```bash
pnpm install
pnpm dev          # 开发（默认 5173）
pnpm build        # 生产构建
pnpm test         # Vitest 单测
pnpm lint         # ESLint
```

## Project Structure

**后端：**
```
ai-interviewer-backend/
├── pom.xml
├── src/main/java/com/aiinterviewer/
│   ├── AiInterviewerApplication.java
│   ├── config/              # CORS / MyBatis-Plus / Spring AI / Tika 配置
│   ├── controller/          # REST 控制器
│   ├── service/             # 业务接口
│   │   └── impl/            # 业务实现
│   ├── mapper/              # MyBatis-Plus Mapper
│   ├── entity/              # 数据库实体（@TableName）
│   ├── dto/                 # 请求/响应 DTO
│   │   ├── req/
│   │   └── resp/
│   ├── graph/               # Spring AI Alibaba graph 编排
│   │   ├── InterviewGraphConfig.java
│   │   ├── nodes/           # graph 节点（开场/提问/判定/总结）
│   │   └── state/           # graph 状态对象
│   ├── skill/               # Skill（判定标准）加载与执行
│   ├── parser/              # 简历/题库文件解析（Tika）
│   ├── exception/           # 全局异常 + @RestControllerAdvice
│   └── common/              # Result<T> / 枚举 / 工具
├── src/main/resources/
│   ├── application.yml
│   ├── mapper/              # MyBatis XML（复杂 SQL）
│   ├── db/
│   │   ├── schema.sql       # 建表（一键创建）
│   │   └── seed.sql         # 30 道种子题
│   └── prompts/             # prompt 模板（开场/提问/判定/总结）
└── src/test/java/           # 测试
```

**前端：**
```
ai-interviewer-frontend/
├── package.json
├── vite.config.ts
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── router/index.ts
│   ├── stores/              # Pinia（model/resume/questionbank/interview）
│   ├── api/                 # axios 封装 + 接口定义
│   ├── composables/         # useSSE / useInterview 等
│   ├── views/
│   │   ├── Home.vue
│   │   ├── Settings.vue     # 模型配置
│   │   ├── Upload.vue       # 简历 + 题库上传
│   │   ├── Interview.vue    # 面试主界面（SSE 流）
│   │   └── Report.vue       # 报告查看
│   ├── components/
│   └── assets/
└── tsconfig.json
```

## Code Style

**后端示例：**
```java
// 实体：Lombok + MyBatis-Plus 注解，snake_case 表名
@Data
@TableName("interview_record")
public class InterviewRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;          // 预留多租户
    private Long modelConfigId;
    private Long skillId;
    private String status;        // enum: RUNNING / FINISHED / ABORTED
    private Integer totalScore;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}

// Service：接口 + Impl 分离
public interface InterviewService {
    InterviewResp start(StartReq req);
    SseEmitter stream(Long interviewId);
    ReportResp getReport(Long interviewId);
}

// 统一返回
@RestControllerAdvice
public class GlobalExceptionHandler { ... }

// Controller 不写业务逻辑，只编排
@RestController
@RequestMapping("/api/interview")
public class InterviewController {
    @PostMapping("/start")
    public Result<Long> start(@RequestBody @Valid StartReq req) {
        return Result.ok(interviewService.start(req).getId());
    }
}
```

**前端示例：**
```vue
<!-- Composition API + <script setup lang="ts"> -->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useInterviewStore } from '@/stores/interview'

const store = useInterviewStore()
const message = ref('')

function send() {
  store.sendMessage(message.value)
  message.value = ''
}
</script>

<template>
  <div class="interview-chat">
    <MessageItem v-for="m in store.messages" :key="m.id" :message="m" />
  </div>
</template>
```

**命名约定：**
- 后端：实体 PascalCase，方法/变量 camelCase，DB 表/列 snake_case，常量 UPPER_SNAKE
- 前端：组件 PascalCase，composable `useXxx`，store `useXxxStore`
- DB 表前缀无（直接 `user` / `model_config` / `question` 等）

## Testing Strategy

**后端：**
- 框架：JUnit 5 + Mockito + Spring Boot Test
- 单元测试：`src/test/java/.../service/`，覆盖 service 层逻辑（mock mapper）
- 集成测试：`src/test/java/.../controller/`，用 MockMvc 测 REST 接口
- graph 节点测试：每个节点单独可测（不依赖真实 LLM，mock ChatClient）
- 简历解析测试：用 `src/test/resources/samples/` 下的样例文件
- 覆盖率目标：service 层 > 70%，不强制 controller 层

**前端：**
- 框架：Vitest + @vue/test-utils
- 关键组件单测：上传校验、SSE 消息渲染、报告渲染
- 不强制 E2E（MVP 阶段）

## Boundaries

**Always do:**
- 改 schema 前先改 spec 和 `schema.sql`
- 提交前跑 `mvn test` 和 `pnpm test`
- 实体字段变更同步更新 `schema.sql`（用 ALTER 而非删表重建）
- API 变更同步更新 DTO + 前端 `api/` 封装
- prompt 模板放 `resources/prompts/`，不硬编码进 Java
- model API key 只存 DB，不进日志、不进 git

**Ask first:**
- 引入新 Maven/npm 依赖
- 修改 `application.yml` 核心配置（DB / Spring AI / 文件存储路径）
- 修改 graph 节点拓扑结构
- 修改 Skill 表结构或评分维度模型

**Never do:**
- 提交真实 model API key 到 git
- 删 `schema.sql` 已有列（必须 ALTER TABLE）
- 在 controller 写业务逻辑（必须下沉 service）
- 跳过 graph 直接调 LLM（所有 LLM 调用走 graph 节点）
- 删除失败测试让构建通过

## Success Criteria

MVP 完成的判定条件（全部满足才算 done）：

1. **模型配置**：用户能在 Settings 页填入 API key / 模型名 / 端点，保存到 DB，面试时按此配置调用 LLM
2. **文件上传**：用户能上传简历（PDF/Word/MD/TXT）和题库（JSON/CSV/MD），后端用 Tika 解析存文本到 DB
3. **开始前校验**：面试开始前若未上传简历或题库，返回明确提示阻止开始
4. **流式对话**：面试过程用 SSE 流式输出 AI 消息，用户能发送回答
5. **graph 线性流程**：面试按 `开场 → 逐题提问 → 逐题判定 → 总结` 节点流转，节点状态可查
6. **逐题判定**：每题回答后，AI 给出分数 + 判定理由，存入 `answer_record` 表
7. **HTML 报告**：面试结束生成报告（每题分 + 总评 + 改进点），前端 Report 页可查看，记录持久化
8. **Skill 切换**：DB 内置默认 Java Skill，用户可在设置里切换（预留接口，MVP 只一个）
9. **DB 一键创建**：执行 `schema.sql` + `seed.sql` 可在空 MySQL 上建出全部表 + 30 道种子题
10. **端到端跑通**：从配置模型 → 上传 → 面试 → 报告，全流程无报错

## 数据库设计（schema.sql 蓝图）

> 完整 DDL 在实现阶段写到 `src/main/resources/db/schema.sql`。这里列出表清单和关键字段，作为 schema 锁定依据。

| 表名 | 用途 | 关键字段 |
|---|---|---|
| `user` | 用户（MVP 单用户，预留） | id, username, created_at |
| `model_config` | 模型 API 配置 | id, user_id, name, provider, api_key, model, endpoint, **judge_model, judge_endpoint**（预留独立 Judge）, is_active |
| `skill` | 判定标准（结构化 prompt） | id, name, position(java/default), prompt_template, scoring_dimensions(json), is_active |
| `resume` | 简历 | id, user_id, filename, raw_text, parsed_text, uploaded_at |
| `question_bank` | 题库 | id, user_id, name, source(seed/user), description |
| `question` | 题目 | id, bank_id, type(theory/scenario/project), difficulty, content, standard_answer, scoring_points(json) |
| `interview_record` | 面试主记录 | id, user_id, model_config_id, skill_id, resume_id, bank_id, status, total_score, start_time, end_time |
| `answer_record` | 每题回答与判定 | id, interview_id, question_id, turn_index, user_answer, ai_question, score, judge_reason, answered_at |
| `interview_report` | 报告 | id, interview_id, total_score, summary, improvement_points(json), generated_at（HTML 由前端 Vue 组件实时渲染，不存） |

**MySQL 连接：** `jdbc:mysql://localhost:3306/ai_interviewer?useSSL=false&serverTimezone=Asia/Shanghai`，账号 root / 密码 123456。数据库名 `ai_interviewer`，由 `schema.sql` 顶部 `CREATE DATABASE IF NOT EXISTS` 创建。

## Open Questions（执行层面，不阻塞 spec）

1. Spring AI Alibaba graph 的具体 API 形态（节点定义/状态传递/条件边）—— 实现时按官方最新文档对齐
2. 简历解析后是否需要结构化抽取（教育/工作经历）？MVP 决定：**只存解析后的纯文本**，不结构化，让 LLM 自己读
3. 题库上传格式约定：JSON（结构化）/ CSV / Markdown？MVP 决定：**支持 JSON 和 Markdown**，JSON 优先
4. 报告 HTML 是后端渲染还是前端渲染？MVP 决定：**前端用 Vue 组件渲染**，后端只存结构化数据 + 文本，不存 HTML

## Phased Roadmap（仅参考，本 spec 只覆盖 Phase 1）

- Phase 1 (MVP)：本 spec 范围
- Phase 2：adaptive probing（条件分支追问）
- Phase 3：知识图谱报告 + 可重练子图
- Phase 4：语音双模态 + 面试官小组 + 面经克隆
- Phase 5+：多用户鉴权 / 并发 / 成本治理
