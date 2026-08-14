# 用户登录与数据隔离

## Problem Statement
如何为 AI 面试官系统添加用户认证和数据隔离，使得不同用户登录后只能看到自己的数据（简历、题库、面试记录、报告），同时保证安全防护到位。

## Recommended Direction
采用 **JWT in httpOnly Cookie** 方案：
- 后端：JWT 认证 + 图形验证码 + 速率限制
- 前端：登录/注册页 + 路由守卫 + 401 拦截
- 数据隔离：用 `UserContext` 线程级上下文替换硬编码 `CURRENT_USER_ID = 1L`

## Architecture Decisions

```
┌─────────────────────────────────────────────────┐
│ 前端 (Vercel)                                    │
│  Login/Register → 提交用户名+密码+验证码           │
│  → 后端返回 httpOnly Cookie (JWT)                │
│  → 后续请求自动携带 Cookie                        │
│  → 401 时跳转登录页                               │
└──────────────────────┬──────────────────────────┘
                       │ httpOnly Cookie (JWT)
                       ▼
┌─────────────────────────────────────────────────┐
│ 后端 (Spring Boot)                                │
│  1. AuthFilter → 解析 Cookie → 提取 userId        │
│  2. UserContext.set(userId) → ThreadLocal         │
│  3. 所有 Service 从 UserContext 读 userId          │
│  4. 请求结束 → UserContext.clear()                 │
│  5. 验证码 + 速率限制保障安全                       │
└─────────────────────────────────────────────────┘
```

## Key Assumptions to Validate
- [ ] 现有 27 处 `UserContext.CURRENT_USER_ID` 全部替换为动态读取后，不会漏改
- [ ] httpOnly Cookie 在跨域场景（Vercel → 后端）能正常工作（需设置 SameSite=None; Secure）
- [ ] 图形验证码在低端安卓设备上可读性足够
- [ ] 速率限制能有效防暴力破解，不影响正常用户

## MVP Scope

### 第一阶段：认证基础设施
- [ ] `user` 表增加 `password_hash` 字段，邮箱、锁定状态等可选
- [ ] JWT 工具类（生成/验证/过期）
- [ ] AuthController（登录/注册/登出/获取当前用户）
- [ ] 图形验证码生成接口
- [ ] JWT AuthFilter（解析 httpOnly Cookie → 设置 UserContext）
- [ ] 配置 CORS + SameSite Cookie

### 第二阶段：数据隔离
- [ ] 将 27 处 `UserContext.CURRENT_USER_ID` 替换为 `UserContext.getUserId()`
- [ ] 创建新用户时 seed 数据初始化（自动复制默认 Skill 记录）
- [ ] 现有数据迁移方案（已有用户的数据归属）

### 第三阶段：安全防护
- [ ] 登录/注册速率限制（每 IP 每分钟最多 5 次尝试）
- [ ] 密码强度校验（最少 8 位，含字母+数字）
- [ ] 输入过滤与参数校验
- [ ] CSRF 防护（Cookie 方案需要）

### 第四阶段：前端
- [ ] 登录页面
- [ ] 注册页面
- [ ] 路由守卫（未登录跳转登录页）
- [ ] Axios 401 拦截器
- [ ] 用户状态 Pinia store

## Not Doing (and Why)
- **OAuth/第三方登录** — MVP 不需要，增加复杂度，后续可加
- **手机号+短信验证码** — 需要短信服务商，增加成本
- **权限角色系统**（管理员/普通用户）— 当前所有人权限相同，后续再加
- **数据库读写分离** — 流量远没到那个规模
- **HTTPS 证书管理** — Vercel 自动处理，后端用反向代理处理
- **密码重置功能** — MVP 可先做"联系管理员重置"，后续再加

## Open Questions
- 现有 seed 数据（3 个 Skill）在新用户注册时如何初始化？每个新用户自动复制一份？
- 已有用户数据（当前 userId=1）如何迁移？保留还是按需迁移？
- 部署时前后端域名不同，跨域 Cookie 是否需要代理（如 /api 反代到后端）？