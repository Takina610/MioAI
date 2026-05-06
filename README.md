# MioAI

MioAI 是一个基于 Spring Boot 3、Spring AI 和 Vue 3 的智能体应用平台，后端提供流式 AI 对话、智能体管理、知识库 RAG、MCP 工具接入、用户体系和文件存储能力，前端提供智能体广场、聊天、知识库管理、MCP 管理和个人中心等页面。

## 功能特性

- 智能体对话：支持默认智能体、CS 游戏助手、MioManus 超级智能体和自定义智能体的 SSE 流式输出。
- 智能体管理：支持智能体创建、编辑、发布、删除、头像上传和应用广场展示。
- 知识库 RAG：支持知识库创建、文档上传、文档解析、向量检索、命中测试和公共知识库。
- MCP 工具：支持 MCP 工具配置、校验、市场展示，并可绑定到自定义智能体。
- 用户体系：支持注册、登录、登出、当前用户获取、个人资料维护和管理员接口。
- 聊天记录：支持会话保存、历史消息、标题生成和对话分享。
- 文件与外部服务：支持 Cloudflare R2 对象存储、Pexels 图片搜索、SearchAPI Web 搜索、PDF 生成等扩展能力。

## 技术栈

### 后端

- Java 21
- Spring Boot 3.4.6
- Spring AI 1.1.4
- Spring AI Alibaba DashScope
- Spring MVC / WebFlux / SSE
- MyBatis-Plus 3.5.11
- MySQL 8
- Redis / Redis Vector Store
- Lombok、Hutool、iText、Jsoup
- AWS SDK S3 兼容存储，用于接入 Cloudflare R2

### 前端

- Vue 3
- Vite
- TypeScript
- Vue Router
- Pinia
- Ant Design Vue
- Axios
- Marked
- Vue Office

## 项目结构

```text
mio-ai
|-- deploy/                         # 部署配置，例如 nginx.conf
|-- mio-ai-front/                   # Vue 3 前端项目
|   |-- src/
|   |   |-- api/                    # 前端接口封装
|   |   |-- components/             # 通用组件
|   |   |-- router/                 # 前端路由
|   |   |-- store/                  # Pinia 状态管理
|   |   |-- types/                  # TypeScript 类型
|   |   |-- utils/                  # 请求、消息、存储等工具
|   |   `-- views/                  # 页面视图
|   |-- package.json
|   `-- vite.config.ts
|-- src/
|   |-- main/
|   |   |-- java/com/mio/ai/
|   |   |   |-- common/             # 通用响应、异常、配置、AOP、工具类
|   |   |   |-- customagent/        # 自定义智能体、知识库、MCP 工具等业务
|   |   |   |-- superagent/         # 默认智能体、CS 助手、MioManus、RAG 与工具调用
|   |   |   |-- user/               # 用户模块
|   |   |   `-- MioAIApplication.java
|   |   `-- resources/
|   |       |-- application.yml     # 后端配置
|   |       |-- mcp/                # MCP 客户端配置
|   |       |-- rag/                # 内置 RAG 文档
|   |       `-- sql/                # Spring AI 聊天记忆表结构
|   `-- test/                       # 单元测试与接口测试
|-- mio_ai.sql                      # 项目数据库初始化脚本
|-- pom.xml                         # Maven 后端依赖
`-- README.md
```

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20+，推荐配合 pnpm 使用
- MySQL 8
- Redis
- 可用的 DashScope API Key
- 如需完整文件能力，还需要配置 Cloudflare R2 或其他 S3 兼容对象存储

## 本地启动

### 1. 初始化数据库

创建并导入数据库脚本：

```bash
mysql -u root -p < mio_ai.sql
```

脚本会创建 `mio_ai` 数据库及项目所需表结构。`src/main/resources/sql/schema-mysql.sql` 用于 Spring AI JDBC Chat Memory 的表结构初始化。

### 2. 启动 Redis

后端默认读取 `application.yml` 中的 Redis 配置。当前配置示例使用：

```text
redis://127.0.0.1:8888
```

如果你的 Redis 使用默认端口，请将配置改为：

```text
redis://localhost:6379
```

### 3. 配置后端

编辑 `src/main/resources/application.yml`，至少确认以下配置：

```yaml
server:
  port: 8081

spring:
  datasource:
    username: your_mysql_user
    password: your_mysql_password
    url: jdbc:mysql://localhost:3306/mio_ai?characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
  data:
    redis:
      url: redis://localhost:6379
  ai:
    dashscope:
      api-key: your_dashscope_api_key

web:
  search:
    api-key: your_searchapi_key

pexels:
  search:
    api-key: your_pexels_key

r2:
  endpoint: your_r2_endpoint
  access-key-id: your_r2_access_key_id
  secret-access-key: your_r2_secret_access_key
  bucket-name: your_bucket_name
  cdn-domain: your_cdn_domain
```

注意：请不要把真实密钥提交到公开仓库。建议后续改造为环境变量或本地私有配置文件。

### 4. 启动后端

在项目根目录执行：

```bash
mvn spring-boot:run
```

后端默认运行在：

```text
http://localhost:8081
```

### 5. 启动前端

进入前端目录：

```bash
cd "mio-ai-front"
pnpm install
pnpm dev
```

前端默认运行在：

```text
http://localhost:3000
```

`mio-ai-front/.env` 默认配置：

```text
VITE_API_BASE_URL=/api
VITE_APP_TITLE=MioAI
```

`vite.config.ts` 中已配置 `/api` 代理到 `http://127.0.0.1:8081`。如果后端没有启用 `/api` context-path，需要在代理中移除或重写 `/api` 前缀，或者打开后端 `server.servlet.context-path=/api`。

## 常用命令

### 后端

```bash
# 启动后端
mvn spring-boot:run

# 运行测试
mvn test

# 打包
mvn clean package
```

### 前端

```bash
cd "mio-ai-front"

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
- `/mio/chat`：MioManus 超级智能体流式对话。
- `/custom/chat`：自定义智能体流式对话。
- `/summary`：对话标题生成。
- `/user/**`：用户注册、登录、资料维护、头像上传。
- `/agents/**`：智能体增删改查、发布、市场列表、头像管理。
- `/knowledge-bases/**`：知识库增删改查、公共知识库、分页查询。
- `/documents/**`：知识库文档上传、处理、查询。
- `/mcp/**`：MCP 工具配置、校验、管理。
- `/agent-mcp/**`、`/agent-knowledge/**`：智能体与 MCP、知识库的绑定关系。
- `/rag-retrieval-logs/**`、`/tool-call-logs/**`、`/agent-usage-logs/**`：检索、工具调用和智能体使用日志。

## 部署说明

前端构建：

```bash
cd "mio-ai-front"
pnpm build
```

构建产物位于 `mio-ai-front/dist`。仓库中提供了 `deploy/nginx.conf`，用于将前端静态文件交给 Nginx，并把 `/api` 请求代理到后端 `8081` 端口。

后端打包：

```bash
mvn clean package
java -jar target/mio-ai-0.0.1-SNAPSHOT.jar
```

生产环境建议：

- 使用环境变量或独立配置文件管理数据库、Redis、API Key、R2 密钥。
- 关闭开发环境 SQL 日志，避免泄露参数和影响性能。
- 为 SSE 接口和上传接口配置合理的 Nginx 超时时间与文件大小限制。
- 为 Redis、MySQL、对象存储配置访问控制和备份策略。

## 注意事项

- 当前后端配置文件中包含外部服务密钥示例，提交公开仓库前请务必替换或迁移到环境变量。
- 数据库脚本 `mio_ai.sql` 包含初始化数据，导入前请确认不会覆盖已有同名数据库。
- 项目依赖 Redis Vector Store，RAG 功能需要 Redis 可用并能初始化向量索引。
- 文件上传依赖 R2 配置，未配置对象存储时头像、知识库文档等上传能力可能不可用。
- 前端通过 `token` 请求头传递登录态，联调时请确认浏览器本地存储和后端 Redis 登录态一致。
