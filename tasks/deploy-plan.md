# 部署方案：Vercel（前端）+ Railway（后端）

## 概述

将 AI 面试官项目部署到线上环境：
- **前端** → Vercel（免费套餐，无限静态站点）
- **后端** → Railway（$5 免费额度，MySQL 插件）
- 两个服务独立域名，通过 CORS + httpOnly Cookie 进行认证通信

## 架构图

```
用户浏览器
    │
    ├── https://myaiinterviewer.vercel.app (Vercel)
    │   └── Vue 3 + Vite 静态站点
    │       └── API 请求 → https://xxx.up.railway.app/api/*
    │
    └── https://xxx.up.railway.app (Railway)
        └── Java 17 + Spring Boot 3.5.4
            └── MySQL (Railway 插件)
```

## 部署前准备

1. 注册 Vercel（https://vercel.com）并关联 GitHub
2. 注册 Railway（https://railway.com）并关联 GitHub
3. 将项目推送到 GitHub 仓库

## 需要修改的文件

| 文件 | 修改类型 | 说明 |
|------|----------|------|
| `ai-interviewer-frontend/vercel.json` | **新建** | Vercel 部署配置（SPA 路由、构建配置） |
| `ai-interviewer-frontend/.env.production` | **新建** | 生产环境变量（后端 API URL） |
| `ai-interviewer-frontend/src/api/http.ts` | **修改** | 根据环境变量拼接 API 基础 URL |
| `ai-interviewer-backend/Dockerfile` | **新建** | 多阶段构建 Spring Boot 镜像 |
| `ai-interviewer-backend/railway.json` | **新建** | Railway 服务配置 |
| `ai-interviewer-backend/src/main/resources/application.yml` | **修改** | 支持环境变量覆盖 DB/JWT 配置 |
| `ai-interviewer-backend/src/main/java/com/aiinterviewer/config/WebConfig.java` | **修改** | 生产环境 CORS 配置（允许 Vercel 域名） |
| `.gitignore` | **新建** | 项目根目录 gitignore |

## 任务列表

### Task 1: 前端 Vercel 部署配置

**描述：** 创建 Vercel 所需的配置文件，包括 SPA 路由重写、构建配置、环境变量。

**文件：**
- `ai-interviewer-frontend/vercel.json`（新建）
- `ai-interviewer-frontend/.env.production`（新建）
- `ai-interviewer-frontend/src/api/http.ts`（修改）

**变更内容：**
1. `vercel.json`：配置 `build.command`（`pnpm build`）、`output.directory`（`dist`）、`rewrites`（SPA 路由）
2. `.env.production`：设置 `VITE_API_BASE_URL` 为 Railway 后端 URL
3. `http.ts`：生产环境使用 `VITE_API_BASE_URL` + `/api` 作为 baseURL

### Task 2: 后端 Railway 部署配置

**描述：** 创建 Dockerfile 和 Railway 配置，使 Spring Boot 应用能在 Railway 上运行。

**文件：**
- `ai-interviewer-backend/Dockerfile`（新建）
- `ai-interviewer-backend/railway.json`（新建）
- `ai-interviewer-backend/src/main/resources/application.yml`（修改）

**变更内容：**
1. `Dockerfile`：多阶段构建（Maven 编译 → JRE 运行）
2. `railway.json`：配置 `build.type` 为 `docker`，`startCommand` 为 `java -jar`
3. `application.yml`：DB 连接、JWT 密钥等配置改为环境变量驱动

### Task 3: CORS 和 Cookie 配置

**描述：** 确保前后端跨域通信正常，httpOnly Cookie 在生产环境正常工作。

**文件：**
- `ai-interviewer-backend/src/main/java/com/aiinterviewer/config/WebConfig.java`（修改）
- `ai-interviewer-backend/src/main/java/com/aiinterviewer/common/AuthFilter.java`（无需修改）

**变更内容：**
1. `WebConfig.java`：CORS `allowedOriginPatterns` 在生产环境限制为 Vercel 域名
2. 确认 Cookie 的 `SameSite=None; Secure` 在 HTTPS 下正常工作

### Task 4: 数据库初始化

**描述：** 确保 Railway MySQL 插件启动后自动创建表结构和种子数据。

**方案：** 使用 Spring Boot 的 `spring.sql.init` 或 Flyway 初始化数据库。

**文件：**
- `ai-interviewer-backend/src/main/resources/application.yml`（修改）
- 或使用 `schema.sql` + `seed.sql` 通过 Spring Boot 自动初始化

### Task 5: 项目根目录 .gitignore

**描述：** 创建项目根目录的 `.gitignore` 文件，排除不需要提交的文件。

**文件：**
- `.gitignore`（新建）

## 环境变量清单

### 前端（Vercel 环境变量）

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `VITE_API_BASE_URL` | 后端 API 基础 URL | `https://xxx.up.railway.app` |

### 后端（Railway 环境变量）

| 变量名 | 说明 | 来源 |
|--------|------|------|
| `MYSQL_URL` | MySQL 连接字符串 | Railway 自动注入 |
| `MYSQL_USER` | MySQL 用户名 | Railway 自动注入 |
| `MYSQL_PASSWORD` | MySQL 密码 | Railway 自动注入 |
| `JWT_SECRET` | JWT 签名密钥 | 手动设置 |
| `CORS_ALLOWED_ORIGINS` | 允许跨域的前端域名 | 手动设置 |

## 部署步骤

### 第一步：修改代码
1. 完成上述所有配置文件的创建和修改
2. 提交代码到 GitHub

### 第二步：Vercel 部署前端
1. 登录 Vercel，导入 GitHub 仓库
2. 选择 `ai-interviewer-frontend` 目录
3. 配置环境变量 `VITE_API_BASE_URL`
4. 部署

### 第三步：Railway 部署后端
1. 登录 Railway，导入 GitHub 仓库
2. 选择 `ai-interviewer-backend` 目录
3. 添加 MySQL 插件
4. 配置环境变量
5. 部署

### 第四步：验证
1. 确认前端可以正常访问
2. 确认注册/登录功能正常
3. 确认面试功能正常