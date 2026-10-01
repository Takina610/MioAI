# MCP 工具添加/校验问题分析与修复（2026-10）

## 现象

部分 MCP 配置添加失败，部分能成功读取工具；另有一些"添加成功但智能体对话时用不了"的情况。

## 根因（按影响排序）

| # | 问题 | 表现 | 修复 |
|---|------|------|------|
| 1 | 传输层只支持 SSE（SDK 0.17 的 `HttpClientSseClientTransport`），不支持 Streamable HTTP | 2025-11 起 MCP 规范主流是 Streamable HTTP（`/mcp` 端点），这类配置校验/运行全部失败（DeepWiki 的 `/sse` 已 410 下线，`/mcp` 才可用） | MCP SDK 升到 2.0，新增 `McpClientFactory`：按 `type` 字段或 URL 形态选择 Streamable/SSE/STDIO，AUTO 模式先 Streamable 后 SSE 回退 |
| 2 | `headers` 字段被静默忽略 | 带认证的远程 MCP（`Authorization: Bearer ...`）一律 401，错误还被误分类为"连接失败" | 两个 HTTP 传输都经 `httpRequestCustomizer` 注入 headers；错误分类补充 401/403/unauthorized |
| 3 | SSE URL 拼接错误 | `url` 带完整路径（如 `https://host/api/sse`）时，SDK 用 `URI.resolve` 拼 `baseUri + /sse`，路径被替换或叠加 | base 取 origin、endpoint 取完整路径，精确还原 URL |
| 4 | 添加接口不校验配置 | 绕过前端两步流程直接调 `POST /mcp`，可存入结构非法的配置，状态还是 ACTIVE，对话时静默失败 | `addMcpTool`/`updateMcpTool` 服务端做结构校验（JSON/mcpServers/url|command） |
| 5 | `config`/`toolInfo` 尺寸限制过小（10k/20k 字符） | 工具多的服务器（schema 很长）校验能通过、添加却报超长 | 放宽到 64k/200k（DB 是 JSON 列，无压力） |
| 6 | STDIO 在 Windows/容器内起不来 | `command: npx` 在 Windows 需要 `cmd /c`（JDK ProcessBuilder 限制）；服务器 Docker 镜像里没有 node | 镜像加 nodejs/npm；Windows 下用 `cmd /c npx` 配置。首次 npx 冷下载可能超 30s 校验超时，重试即可 |
| 7 | `env` 值类型不严 | `{"PORT": 3000}` 数字值 → 子进程启动时 ClassCastException，且错误信息难懂 | env 值统一 `String.valueOf` |
| 8 | 多服务器节点静默丢弃 | 配置里有多个 `mcpServers` 节点时只取第一个，其余无声丢失 | 校验结果返回 `warnings`，前端弹窗展示 |
| 9 | 部分更新清空字段 | `updateMcpTool` 用 Hutool 默认 copyProperties（不忽略 null），只改名称会把 config 置空 | `setIgnoreNullValue(true)` |
| 10 | 客户端缓存不失效 | 更新配置/删除工具后缓存里还是旧连接 | 新增 `evictClient(mcpId)`，update/delete 时调用 |
| 11 | 校验通过 ≠ 运行时可用 | 校验服务与运行时客户端是两套代码，行为可能不一致 | 抽出共用的 `McpClientFactory`，两端同一实现 |

## 版本迁移（同批完成）

- Spring Boot 3.4.6 → **4.1.1**（Tomcat 11、Jackson 3 默认、starter-web→webmvc、aop→aspectj、MockMvc 需 @AutoConfigureMockMvc）
- Spring AI 1.1.4 → **2.0.1**：`spring-ai-advisors-vector-store`→`spring-ai-vector-store-advisor`；`internalToolExecutionEnabled` 移除、ChatClient 自动挂 ToolCallAdvisor → ToolCallAgent 手动工具循环改直连 ChatModel（`Prompt.augmentSystemMessage` 合并系统提示），会话记忆由代理自主持久化（user/assistant/tool 消息写入 ChatMemory，等价原 MessageChatMemoryAdvisor 行为）
- DashScope：spring-ai-alibaba 无 2.0 GA 版 → 官方 `spring-ai-starter-model-openai` + 兼容模式 `https://dashscope.aliyuncs.com/compatible-mode/v1`（2.0 的 openai-java 底层不再自动补 /v1，base-url 必须带）。重排走 DashScope 原生 text-rerank HTTP（自实现 `DashScopeHttpRerankClient` + `RetrievalRerankAdvisor`）
- Jackson 2 → 3：XSS 模块、JacksonUtil、BaseAgent 全部迁到 `tools.jackson`（Boot 4 HTTP 层用 Jackson 3，旧模块会静默失效）
- MyBatis-Plus → **3.5.16** boot4-starter（3.5.17 把 IService/ServiceImpl 挪到 spring 包，破坏性变更，不跟）
- MCP Java SDK 0.17.0 → **2.0.0**（spring-ai 2.0.1 管理的版本；`JacksonMcpJsonMapper` 在 jackson3 包，`InitializeResult` 需从 initialize() 返回值拿）
- Redis 向量库：SA 2.0 自动配置强制要求 `JedisConnectionFactory` → `spring.data.redis.client-type: jedis`

## 验证记录（2026-10-01 本地）

- 应用在 Boot 4.1.1/Tomcat 11 上完整启动（SA 2.0.1 + MP 3.5.16 + MCP SDK 2.0 全链路装配成功）
- Streamable HTTP：`https://mcp.deepwiki.com/mcp` → success，protocol `2025-11-25`，读回工具列表（旧代码该配置必失败）
- SSE：本地 `@modelcontextprotocol/server-everything sse` → success，protocol `2024-11-05`
- STDIO：`cmd /c npx -y @modelcontextprotocol/server-everything` → success（首次冷下载超 30s 超时，缓存后 2s 成功）
- AUTO 回退：`http://127.0.0.1:3001`（无路径）→ Streamable 失败自动回退 SSE 成功
- 多服务器配置 → success + warning「仅校验并使用第一个」
- 添加接口：非法配置被 40000 拒绝；合法配置入库；只改名称的更新不丢 config

## 遗留

- 服务器（192.168.1.20）部署：迁移后尚未部署，当前网络 SSH 不可达（代理拦截）。恢复后执行 `python .deploy/deploy_mioai.py`（或手动上传 jar → `docker compose up -d --build mioai-server`）。
- 对话级 E2E（MCP 工具真正参与聊天）需在服务器有 DASHSCOPE_API_KEY 的环境验证。
