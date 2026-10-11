# MioAI Agent 演进路线图

> 2026-10-11 生成。对照 [learn-agent 20 章教程](https://goehou.github.io/learn-agent/)（从最小 Agent Loop 到完整 Harness）、zcode / codex / opencode / pi 四个开源引擎，盘点 MioAI zagent 引擎的现状与缺口。健壮性加固主线见 2026-10-11 两笔提交（5fcfb81 及其后续一笔）。

## 现状总评

引擎骨架（Loop / 工具 / 压缩 / 兜底 / 持久化）已对位 zcode 移植完成，单机单用户落地稳定。
差距集中在：**权限与审批、多 Agent 协作、记忆系统、任务调度、可观测**——恰是 learn-agent 后半本教材的主题。

## learn-agent 20 章对照

| 章 | 主题 | MioAI 现状 | 演进项 |
|---|---|---|---|
| 01 | Agent Loop | ✅ zcode 对位（maxSteps 安全网+末步禁工具收束） | — |
| 02 | 工具接入 | ✅ 16 内置工具+注册表统一执行 | — |
| 03 | 权限系统 | ⚠️ 无审批门（Bash/Write/Edit 自动执行） | 三态规则（allow/ask/deny）+ AskUserQuestion 通道做审批 UI + "always" 记忆 |
| 04 | Hook 解耦 | ✅ beforeToolCall/afterToolCall 钩子全集（AgentHooks，可拦截可审计，chain 组合） | 工具调用审计已解耦为 ToolAuditHook 插件 |
| 05 | 长上下文提示词失效 | ⚠️ 压缩已有，但 system prompt 每步全量注入 | 关键指令后置重复（system-reminder 已是此语义） |
| 06 | 多 Agent 协作 | ✅ 子代理（档案白名单+镜像事件，不嵌套） | 团队化协作（见 16-18） |
| 07 | Skill 技能系统 | ✅ 已有（SKILL.md 物化+清单注入+市场） | 技能级权限/评分 |
| 08 | 上下文压缩 | ✅ 自动+手动+微压缩+反应式+双重熔断 | token 用量精确化（provider usage 锚点优先，字符估算兜底） |
| 09 | 文件级持久化记忆 | ✅ 沙箱 memory/&lt;agentId&gt;/ 目录：MEMORY.md 索引+记忆 md 文件，会话开始注入上下文，模型用 Write/Read 自管理；智能体管理页 CRUD | 记忆召回去重/衰减 |
| 10 | 模块化架构 | ✅ zagent 引擎与产品封装解耦 | — |
| 11 | API 韧性 | ✅ 分类重试+Retry-After+idle 超时+空补全重试+熔断 | 多 key 池/传输降级（单反代下暂无必要） |
| 12 | 任务引擎 | ✅ run_in_background 沙箱脱离执行（setsid+nohup，后端重启进程存活）+ agent_background_task 持久化 + TaskOutput 探针重连/TaskStop 杀进程组 + 重启对账 | 慢操作进度事件（tool.progress） |
| 13 | 异步慢操作 | ✅ run_in_background bash+子代理后台 | 慢操作进度事件（tool.progress） |
| 14 | Cron 调度 | ❌ 无 | 定时任务工具（对齐 zcode CronCreate 语义） |
| 15 | Inbox 注入 | ⚠️ task-notification 下一轮注入（同思想） | 跨会话消息队列 |
| 16-17 | 多 Agent 团队 | ❌ 单层子代理 | 团队/轮询模式（远期） |
| 18 | 并行文件冲突 | ➖ 单 bot 单沙箱设计下天然规避 | — |
| 19 | MCP 动态工具池 | ✅ mcp__ 前缀+失败清单+300s 超时 | OAuth、多节点、stderr 透传 |
| 20 | 总集成 | — | 以上按序补齐 |

## 参考引擎遗留可移植项

| 来源 | 设计 | 优先级 |
|---|---|---|
| pi | length 截断续写（zcode 3 次配额续写，非仅作废） | P3 |
| codex | 多级重试预算分离（采样/连接/传输各自计数）、x-should-retry 尊重 | P3 |
| opencode | 权限三态+Deferred 挂起+always 记忆；durable event sourcing（seq 持久化重放） | P2/P3 |
| opencode2api | 多 key 指数冷却池、会话亲和 | P3（多 provider 时） |
| zcode | 压缩 prompt-too-long 重试、thinking 签名自修复、请求准入闸门 | P3 |

## 本轮已修（2026-10-11 第二笔提交）

限流（每归属 10 轮/分钟 + 3 并发上限，Redis 分钟窗口）、tool_call_log 真实状态/耗时/结果/参数、
注入防御（知识库片段与 WebFetch 内容显式"数据非指令"框架）、三处坏 schema（TodoWrite/AskUserQuestion
括号错位、GlobTool formatted 注入未做 JSON 转义——此前这三个工具一直以无参数 schema 的状态发给模型）、
注册期 schema 校验（坏 schema 显式报错而非静默降级）、游客 usage 落库跳过、Retry-After 尊重、
length 截断工具调用作废+强制收束。

## 本轮已修（2026-10-11 第三笔：三项缺陷补齐）

**Hook 解耦**：`AgentHooks`（beforeToolCall 可拦截/afterToolCall 观测，chain 组合）挂入
ToolRegistry 执行链；tool_call_log 审计从事件通道迁出为 `ToolAuditHook` 插件，引擎核心
不再硬编码横切关注点；子代理注册表沿用运行级钩子。

**文件级持久化记忆**（对齐 zcode per-project memory）：沙箱工作区 `memory/<agentId>/`，
MEMORY.md 为索引 + 单事实记忆 md 文件（frontmatter name/description/type）；会话开始由
ContextBuilder 注入索引与用法说明（游客不注入）；模型用通用 Write/Read 工具自管理（不做
领域工具）；智能体编辑页新增"记忆"面板（列表/编辑/新建/删除，保存自动登记索引指针行，
删除同步清除索引行，路径白名单防穿越）。

**任务引擎持久化**：run_in_background bash 改为沙箱脱离执行（`true; setsid nohup … <
/dev/null > out 2>&1 &`——stdin 不脱离/套子壳/被 `cd &&` 绑定都会让 SSH exec 通道等到
超时，三个坑全踩过）；~/.mio-tasks/<taskId>.{sh,out,code,pid} 四件套；任务落
agent_background_task 表（detached/pid/deadline 随 touch 回写），后端重启后 TaskOutput
经探针重连收尾、轮次开始 refreshDetached 补通知、启动对账把进程内任务改判 interrupted、
注册序号自库内最大值续接；TaskStop 真杀沙箱进程组（TERM→KILL），子代理任务打断执行线程。

## 明确不做（设计如此）

- 沙箱连接池/工作区隔离：设计初衷即一个 bot 独占一台云电脑任意操作。
