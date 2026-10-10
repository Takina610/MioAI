# MioAI

MioAI 是一个基于 Spring Boot 3、Spring AI 和 Vue 3 的智能体应用平台，后端提供流式 AI 对话、智能体管理、知识库 RAG、MCP 工具接入、用户体系和文件存储能力，前端提供智能体广场、聊天、知识库管理、MCP 管理和个人中心等页面。

## 功能特性

- 智能体对话：支持默认智能体、CS 游戏助手、MioManus 超级智能体和自定义智能体的 SSE 流式输出。
- 智能体管理：支持智能体创建、编辑、发布、删除、头像上传和应用广场展示。
- 知识库 RAG：支持知识库创建、文档上传、文档解析、向量检索、命中测试和公共知识库。
- **知识库绑定与检索隔离**：自定义智能体只检索其绑定的知识库（向量过滤 `kbId`），每个绑定可独立配置 topK / 相似度阈值 / 是否重排（`agent_knowledge.retrieval_config`）。
- **MioManus 显式规划**：对标 Manus 的任务清单机制，执行前创建计划、每步更新状态，计划实时注入思考提示词。
- **长会话摘要记忆**：超过阈值的历史对话自动压缩为摘要注入上下文，避免固定窗口遗忘早期信息。
- **RAG 消融实验**：内置 30 条评测集与检索评测脚本，支持分块大小/关键词增强/重排的单变量对比实验（见 `doc/rag-eval-experiment.md`）。
- MCP 工具：支持 MCP 工具配置、校验、市场展示，并可绑定到自定义智能体（支持绑定级 `config_override` 覆盖密钥等参数）。
- 用户体系：支持注册、登录、登出、当前用户获取、个人资料维护和管理员接口（密码 BCrypt 存储，历史 MD5 口令登录时自动升级）。
- 聊天记录：支持会话保存、历史消息、标题生成和对话分享。
- 文件与外部服务：支持 Cloudflare R2 对象存储、Pexels 图片搜索、SearchAPI Web 搜索、PDF 生成等扩展能力。
- 安全基线：接口级资源归属校验、终端命令白名单、XSS 过滤、前端路由守卫。

## 技术栈

### 后端

- Java 21
- Spring Boot 4.1.1
- Spring AI 2.0.1
- Spring AI OpenAI 兼容接入（对话走本地 opencode2api/zen 网关，Embedding 走 42x 网关 mistral-embed）
- Spring MVC / WebFlux / SSE
- MyBatis-Plus 3.5.11
- MySQL 8
- PostgreSQL 16 + pgvector（向量库）/ Redis（缓存）
- Lombok、Hutool、iText、Jsoup、spring-security-crypto（BCrypt）
- AWS SDK S3 兼容存储，用于接入 Cloudflare R2

### 前端

- Vue 3
- Vite
- TypeScript
- Vue Router（含全局路由守卫）
- Pinia
- Ant Design Vue
- Axios
- Marked
- Vue Office

## 项目结构

```text
mio-ai
|-- MioAIBackend/                   # Spring Boot 3 后端项目
|   |-- Dockerfile                  # 后端镜像（多阶段构建）
|   |-- settings.xml                # Docker 构建专用：Maven Central 阿里云镜像
|   |-- mio_ai.sql                  # 数据库初始化脚本（纯结构 + 种子数据，无真实数据）
|   |-- doc/
|   |   `-- rag-eval-experiment.md  # RAG 消融实验设计与论文写作指引
|   |-- pom.xml                     # Maven 后端依赖
|   `-- src/
|       |-- main/
|       |   |-- java/com/mio/ai/
|       |   |   |-- common/             # 通用响应、异常、配置、AOP、工具类
|       |   |   |-- customagent/        # 自定义智能体、知识库、MCP 工具、检索隔离等业务
|       |   |   |-- superagent/         # 默认智能体、CS 助手、MioManus（含显式规划）、摘要记忆与工具
|       |   |   |-- user/               # 用户模块
|       |   |   `-- MioAIApplication.java
|       |   `-- resources/
|       |       |-- application.yml.example  # 后端配置模板（复制为 application.yml 后填写）
|       |       |-- rag/                # 内置 RAG 文档
|       |       `-- sql/                # Spring AI 聊天记忆表结构
|       `-- test/                       # 纯单元测试（无需外部环境）+ 集成/评测测试（需配置环境）
|-- MioAIFrontend/                  # Vue 3 前端项目
|   |-- Dockerfile                  # 前端镜像（构建 + nginx）
|   |-- nginx.conf                  # 容器内 nginx 配置（静态托管 + /api 反代 + SSE）
|   |-- src/
|   |   |-- api/                    # 前端接口封装
|   |   |-- components/             # 通用组件
|   |   |-- router/                 # 前端路由（含 requiresAuth/requiresAdmin 守卫）
|   |   |-- store/                  # Pinia 状态管理
|   |   |-- types/                  # TypeScript 类型
|   |   |-- utils/                  # 请求、消息、存储等工具
|   |   `-- views/                  # 页面视图
|   |-- package.json
|   `-- vite.config.ts
|-- docker-compose.yml              # 一键部署：MySQL + PostgreSQL(pgvector) + Redis + 后端 + 前端
|-- .env.example                    # 部署环境变量模板
`-- README.md
```

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20+，推荐配合 pnpm 使用
- MySQL 8
- PostgreSQL 16（向量检索依赖 pgvector 扩展）+ Redis（缓存）
- 可用的 Embedding API Key（OpenAI 兼容 /v1/embeddings，默认 42x 网关 mistral-embed）
- 如需完整文件能力，还需要配置 Cloudflare R2 或其他 S3 兼容对象存储

## 本地启动

### 1. 初始化数据库

```bash
mysql -u root -p < mio_ai.sql
```

脚本会创建 `mio_ai` 数据库及全部表结构，并写入种子数据（内置智能体 + 默认管理员 `admin / admin123456`，**首次登录后请立即修改密码**）。脚本只包含结构与种子数据，不含任何真实聊天记录或密钥。

### 2. 配置后端

```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
```

编辑 `application.yml`，至少填写 MySQL、Redis、Embedding API Key（`EMBED-APIKEY` 或 `EMBED_APIKEY`）、R2 配置。`application.yml` 已被 `.gitignore` 排除，不会误提交；所有敏感项均支持用同名大写环境变量覆盖。

### 3. 启动后端

```bash
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8081`。

### 4. 启动前端

```bash
cd "MioAIFrontend"
pnpm install
pnpm dev
```

前端默认运行在 `http://localhost:3000`，`vite.config.ts` 已将 `/api` 代理到 `http://127.0.0.1:8081`。

## Docker 一键部署

```bash
cp .env.example .env      # 填入真实密钥
docker compose up -d --build
# 首次访问 http://localhost:8090（数据库结构已由 compose 初始化脚本自动导入）
```

## 常用命令

### 后端

```bash
# 启动后端
mvn spring-boot:run

# 运行纯单元测试（无需 MySQL/Redis/密钥）
mvn test

# 运行 RAG 检索评测（需要真实环境，详见 doc/rag-eval-experiment.md）
RAG_EVAL=true RAG_EVAL_KB_ID=1 mvn test -Dtest=RagEvaluationTest

# 打包
mvn clean package
```

### 前端

```bash
cd "MioAIFrontend"

# 开发环境
pnpm dev

# 类型检查
pnpm type-check

# 构建生产包
pnpm build

# 本地预览生产包
pnpm preview
```

## 主要接口方向

- `/chat`：默认智能体流式对话。
- `/cs/chat`：CS 游戏助手流式对话。
- `/mio/chat`：MioManus 超级智能体流式对话（含显式任务规划）。
- `/custom/chat`：自定义智能体流式对话（仅检索绑定知识库）。
- `/summary`：对话标题生成。
- `/user/**`：用户注册、登录、资料维护、头像上传。
- `/agents/**`：智能体增删改查、发布、市场列表、头像管理。
- `/knowledge-bases/**`：知识库增删改查、公共知识库、分页查询。
- `/documents/**`：知识库文档上传、处理、查询、命中测试（按知识库过滤）。
- `/mcp/**`：MCP 工具配置、校验、管理。
- `/agent-mcp/**`、`/agent-knowledge/**`：智能体与 MCP、知识库的绑定关系（含归属校验）。
- `/rag-retrieval-logs/**`、`/tool-call-logs/**`、`/agent-usage-logs/**`：检索、工具调用和智能体使用日志。
- `/usage-stats/**`、`/admin/**`：用户/管理员使用统计与后台管理。

## 安全设计要点

- **资源归属校验**：会话、文档、知识库、智能体、MCP 绑定等资源均校验所有者（公开资源按可见性规则放行），见 `AccessGuardService`。
- **检索隔离**：向量检索通过 `filterExpression` 限定 `kbId` 范围，自定义智能体无法跨用户检索。
- **终端命令白名单**：MioManus 的终端工具仅允许白名单内单条命令，拒绝管道/链式/重定向（`TerminalCommandPolicy`，可经 `mio.ai.tools.terminal.allowed-commands` 配置）。
- **密码安全**：BCrypt 存储；历史 MD5 口令在登录成功后自动升级，`mio_ai.sql` 不含真实用户口令。
- **密钥管理**：`application.yml`、`.env` 均不入库，配置模板与应用分离；历史泄露的密钥请到对应平台作废重发。

## 生产环境建议

- 使用环境变量或独立配置文件管理数据库、Redis、API Key、R2 密钥。
- 关闭开发环境 SQL 日志，避免泄露参数和影响性能。
- 为 SSE 接口和上传接口配置合理的 Nginx 超时时间与文件大小限制（`MioAIFrontend/nginx.conf` 已按 SSE 调整）。
- 为 Redis、MySQL、对象存储配置访问控制和备份策略。
- SSE 鉴权当前通过 URL 传 token（EventSource 限制），生产环境建议改为一次性短票据换取连接。

## 注意事项

- 向量数据存 PostgreSQL（pgvector），普通 Redis 只作缓存，不再承担向量检索。
- 文件上传依赖 R2 配置，未配置对象存储时头像、知识库文档等上传能力可能不可用。
- 前端通过 `token` 请求头传递登录态，联调时请确认浏览器本地存储和后端 Redis 登录态一致。
