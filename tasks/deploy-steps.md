# AI 面试官 部署操作指南

## 前置条件
- GitHub 仓库: `mitiy001/ai-interviewer`（代码已推送完成）
- 已有 Vercel 账号: [https://vercel.com/myaiinterviewer](https://vercel.com/myaiinterviewer)（用户已确认）
- 已有 Railway 账号: [https://railway.com](https://railway.com)

---

## 第一步：Vercel 部署前端

### 1.1 登录 Vercel 并导入项目
1. 打开 [https://vercel.com/myaiinterviewer](https://vercel.com/myaiinterviewer)
2. 点击 **"Add New..."** → **"Project"**
3. 在 "Import Git Repository" 下找到 `ai-interviewer` 仓库，点击 **"Import"**

### 1.2 配置项目
1. **Framework Preset**: 自动检测为 **Vite**（或手动选择）
2. **Root Directory**: 点击 **"Edit"**，选择 `ai-interviewer-frontend`
3. **Build and Output Settings**（会自动从 `vercel.json` 读取）：
   - Build Command: `pnpm build`
   - Output Directory: `dist`
   - Install Command: `pnpm install --no-frozen-lockfile`

### 1.3 配置环境变量
点击 **"Environment Variables"**，添加：

| 变量名 | 值 |
|--------|-----|
| `VITE_API_BASE_URL` | `https://ai-interviewer-backend.up.railway.app` |

> ⚠ 注意：这个 URL 是后端 Railway 的默认域名，等后端部署完成后如果实际域名不同，需要回来更新这个值。

### 1.4 部署
1. 点击 **"Deploy"** 按钮
2. 等待部署完成（约 1-2 分钟）
3. 部署成功后，Vercel 会分配一个域名，例如 `https://ai-interviewer.vercel.app`
4. 点击 **"Visit"** 查看前端页面

### 1.5 验证前端
- [ ] 打开前端页面，确认页面正常加载
- [ ] 确认注册页面可访问
- [ ] 确认登录页面可访问

---

## 第二步：Railway 部署后端

### 2.1 登录 Railway 并导入项目
1. 打开 [https://railway.com](https://railway.com)
2. 点击 **"New Project"**
3. 选择 **"Deploy from GitHub repo"**
4. 授权 GitHub 后，选择 `mitiy001/ai-interviewer` 仓库
5. 点击 **"Add Variables"** 旁的 **"Configure"**
6. 在 **"Root Directory"** 中输入 `ai-interviewer-backend`
7. 点击 **"Deploy"**

### 2.2 添加 MySQL 数据库
1. 在项目仪表板中，点击 **"New"** → **"Database"** → **"Add MySQL"**
2. 等待 MySQL 插件启动完成（约 1-2 分钟）
3. 启动完成后，点击 MySQL 服务，在 **"Connect"** 标签页中查看以下信息：
   - `MYSQL_URL`（数据库连接字符串，如 `jdbc:mysql://...`）
   - `MYSQL_USER`（用户名）
   - `MYSQL_PASSWORD`（密码）
   - `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DATABASE`

### 2.3 配置后端环境变量
1. 点击后端服务（Java 应用），进入 **"Variables"** 标签页
2. 添加以下环境变量：

| 变量名 | 值 | 说明 |
|--------|-----|------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}?useSSL=true&requireSSL=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8` | MySQL 连接字符串 |
| `SPRING_DATASOURCE_USERNAME` | `${MYSQL_USER}` | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | `${MYSQL_PASSWORD}` | 数据库密码 |
| `JWT_SECRET` | `AiInterviewer-Jwt-SecretKey-2024-MustBeAtLeast32Chars!!` | JWT 签名密钥（可改为更复杂的随机字符串） |
| `CORS_ALLOWED_ORIGINS` | `https://ai-interviewer.vercel.app` | 允许跨域的前端域名（Vercel 部署后得到的实际域名） |
| `SPRING_SQL_INIT_MODE` | `always` | 首次部署时自动建表+导入种子数据 |

> ⚠ 注意：Railway 支持变量引用，`${MYSQL_HOST}` 会自动读取 MySQL 插件的环境变量。

### 2.4 重新部署
1. 环境变量配置完成后，进入 **"Deployments"** 标签页
2. 点击 **"Redeploy"** 重新部署
3. 等待构建和部署完成（约 3-5 分钟，Docker 构建需要下载依赖）
4. 查看部署日志，确认无错误

### 2.5 验证后端
1. 部署成功后，在 **"Settings"** 标签页找到生成的域名（如 `https://ai-interviewer-backend.up.railway.app`）
2. 访问健康检查接口：`https://ai-interviewer-backend.up.railway.app/actuator/health`
3. 预期响应：`{"status":"UP"}`

---

## 第三步：更新前端环境变量

### 3.1 确认后端域名
1. 从 Railway 后端服务的 **Settings** 页面复制域名
2. 例如：`https://ai-interviewer-backend.up.railway.app`

### 3.2 更新 Vercel 环境变量
1. 回到 Vercel 项目仪表板
2. 进入 **"Settings"** → **"Environment Variables"**
3. 更新 `VITE_API_BASE_URL` 为实际的 Railway 后端域名
4. 点击 **"Save"**

### 3.3 重新部署前端
1. 进入 **"Deployments"** 标签页
2. 找到最新的部署，点击 **"..."** → **"Redeploy"**
3. 等待部署完成

---

## 第四步：端到端验证

### 4.1 核心功能验证
- [ ] 打开前端页面，确认能正常加载
- [ ] 点击 **注册**，创建一个新账号
- [ ] 注册成功后自动跳转到首页
- [ ] 退出登录，重新用刚注册的账号登录
- [ ] 登录成功后进入首页，确认功能正常
- [ ] 检查技能列表是否正常加载（Java 初级/中级/高级面试官）
- [ ] 尝试激活一个技能
- [ ] 配置模型 API（需要填入真实的 API Key 和端点）
- [ ] 上传简历，确认简历解析正常
- [ ] 开始一场面试，确认面试流程正常

### 4.2 Cookie 验证
- [ ] 打开浏览器开发者工具 → Application → Cookies
- [ ] 确认存在 `token` Cookie
- [ ] Cookie 属性：`HttpOnly`、`SameSite=None`、`Secure`

---

## 常见问题处理

### 问题 1：前端页面白屏或加载失败
- 检查 Vercel 构建日志，看是否有构建错误
- 确认 `VITE_API_BASE_URL` 环境变量已正确设置
- 确认 `vercel.json` 中的 `rewrites` 配置正确

### 问题 2：后端健康检查失败
- 检查 Railway 部署日志，看是否有编译错误
- 确认 MySQL 连接信息正确
- 确认 `SPRING_SQL_INIT_MODE` 为 `always`（首次部署）
- 检查 Dockerfile 是否构建成功

### 问题 3：登录/注册提示"未登录"
- 确认 `CORS_ALLOWED_ORIGINS` 设置为前端实际域名
- 确认 Cookie 的 `SameSite` 和 `Secure` 属性正确
- 检查后端日志中是否有异常信息

### 问题 4：CORS 跨域错误
- 确认 `CORS_ALLOWED_ORIGINS` 包含前端实际域名
- 如果使用 `http://localhost` 开发，也需添加本地域名
- 多个域名用逗号分隔：`https://ai-interviewer.vercel.app,http://localhost:5173`

### 问题 5：数据库表未创建
- 检查 `SPRING_SQL_INIT_MODE` 是否设为 `always`（首次）
- 检查 `schema-tables.sql` 和 `seed.sql` 文件路径是否正确
- 首次部署成功后，建议将 `SPRING_SQL_INIT_MODE` 改为 `never` 避免重复初始化

---

## 环境变量完整清单

### Vercel（前端）

| 变量名 | 必填 | 说明 |
|--------|------|------|
| `VITE_API_BASE_URL` | 是 | 后端 API 地址，如 `https://ai-interviewer-backend.up.railway.app` |

### Railway（后端）

| 变量名 | 必填 | 说明 |
|--------|------|------|
| `SPRING_DATASOURCE_URL` | 是 | MySQL 连接字符串 |
| `SPRING_DATASOURCE_USERNAME` | 是 | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | 是 | 数据库密码 |
| `JWT_SECRET` | 是 | JWT 签名密钥，至少 32 字符 |
| `CORS_ALLOWED_ORIGINS` | 是 | 允许跨域的前端域名 |
| `SPRING_SQL_INIT_MODE` | 首次=是 | 首次设为 `always`，之后改为 `never` |