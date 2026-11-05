# Super-agent 设计与护群聊调度对照

> 2026-11-05。研读《Super-agent 设计探索：迈向更自主的 Agent 系统》（DeepSeek Harness 团队，<https://www.bilibili.com/opus/1265361021143351318>）后，逐条比对本项目现有的群聊调度实现，记录采纳了什么、为什么，以及明确不采纳的部分。

## 一句话结论

文章的双层架构（Router / Worker）在本项目的群聊里**结构上已经具备**——群主是 Router，成员是 Worker。但我方缺的是文章强调的两个"系统层面"约束，靠提示词补不上，已经在后端补齐；其余模块（事件驱动、存算分离、Watchdog）与当前产品形态不匹配，不采纳。

## 逐项对照

### 1. 双层架构（§2）— 已具备，补了两处结构缺口

| 文章要点 | 本项目状况 |
|---|---|
| Router 常驻、只做分派与综合，不执行具体任务 | 群主提示词原先明写"你可以亲自执行任务"；已改为只保留分派/检索/UI 工具，环境工具在**注册表层面不装配** |
| Worker 上下文隔离，专注单任务 | 成员各跑独立 `AgentEngine`，已是隔离的 |
| Worker 不直接面对人，由 Router 统一回复 | 成员回答以 member 块呈现，群主收尾综合——已是此设计 |

**采纳 1：Router 的工具面边界**（`GroupPrompts.ORCHESTRATOR_TOOLS`）

文章 §7.1 的消融实验给出了明确数据：单 Session + Subagent 方案下，中性提示 5 次运行**一次都没委派**，p95 完成时间 29.5 分钟；改成"提示优先委派"后仍有 1 次模型自行判断"这活很小"而自己干，形成长尾。结论是**靠提示词约束分工不可靠，要靠系统结构**。

我方原先正是"提示词里写可以自己干"的结构，因此做了两件事：

- 白名单制工具面：群聊主轮次只装配 `Agent / WebSearch / WebFetch / TodoRead / TodoWrite / TaskOutput / TaskStop / AskUserQuestion`，`Read/Write/Edit/Bash/Glob/Grep` 直接不注册（`ToolsetFactory` 里 `Read` 原先无条件注册，已收进白名单）。
- 提示词同步改写："其余任务一律委派"，并说明自己没有文件工具。

> 顺带修了一个真实缺陷：`Read` 原先不受白名单约束，导致只读档案（Explore）实际上仍能拿到 `Write`/`Edit`。

**采纳 2：Agent 工具描述随群聊改写**（`GroupPrompts.agentToolDescription`）

这是一个**实测发现的缺陷**。框架默认的 `AGENT_HEADER` 会向模型列出 `general-purpose` / `Explore` 两个内置档案，但群聊里这两个类型根本不存在。真实轮次的思考链里出现了：

> "I should use the Agent tool with subagent_type... but available subagent types are explore/general"

模型因此先试错、再放弃委派、最后自己硬做——正是文章 §7.1 描述的失败模式。现在群聊的 Agent 工具描述只列真实成员名。

### 2. 上下文组织 Inline & Spill（§3.3）— 采纳，改造为「成员线程」

文章把上下文窗口当 Cache 管理：高概率用到的内联（Inline），低频的留在窗口外只留引用（Spill），并对 Trigger/Thread/Session 赋 ID 构成引用图，实现链式召回。

本项目的缺陷在于：**成员的回答只落库成展示块，从没进过请求历史**。`ConversationHydrator` 只认 `text` 与 `tool` 块，`member` 块被整块丢弃。后果是下一轮群主完全不知道自己派过谁、成员给过什么结论——只能凭对话里的文本复述或重新委派，长会话里必然漂移。

**采纳 3：成员发言回流请求历史**

- `member` 块水合为带署名的 `system-reminder`（`source="member"`），按原始时间序插在群主文本/工具之间。
- 失败或空发言不注入——成员没产出内容，注入只会让群主误判它给过结论。
- 同时按 `memberId` 归档成本会话的成员历史。

**采纳 4：成员线程续接（Thread 的会话内对位）**

归档的成员历史在**下次委派该成员时**内联进它的系统提示，成员不必每轮从零开始，群主也不必反复复述前情。这是文章"订阅 Thread 后 Worker 直接拿到该 Thread 的后续事件"在本项目（单会话、无外部事件源）下的等价简化。

边界处理：

- 只带该成员自己的发言，不串号（测试覆盖）。
- 只保留最近 3 条，单条截断 1500 字符，总量 4000 字符——**从最新往前累积**，超限时丢弃的是更早的发言而不是刚说过的结论。
- 归档来源有两处：跨轮（历史水合）与轮内（同一轮再次委派同一成员）。

### 3. 事件驱动（§3.1–3.2、§3.4）— 不采纳

| 文章机制 | 不采纳理由 |
|---|---|
| Fast/Slow Path 两级事件过滤 | 本项目的事件源就是用户在聊天框里发的一条消息，本身就是高价值信号，没有"高噪音低触发率"的环境事件流需要过滤 |
| Thread 订阅与直通（多对多） | 群聊是单会话串行轮次，不存在"同一 PR/邮件串的后续事件直接投递给多个 Worker"的场景；多成员并行委派已由同轮多个 Agent 调用覆盖 |
| 投机执行（后台提前行动） | 群主在用户发消息前没有任何环境信号可依据，无从投机 |

### 4. 全双工交互（§4.1）— 部分具备

文章的痛点是 ReAct Loop 里同步工具调用阻塞会话、期间无法接收新输入。本项目已有 `run_in_background` + `TaskOutput/TaskStop` 的异步委派，成员也可后台运行，方向一致。

不做的是"轮次进行中接收新用户消息并插队"——当前产品是用"会话占用（`ACTIVE_CHATS`）"显式拒绝，用户能看到明确的忙碌提示。改成插队需要重构 SSE 与消息时序，收益不足以支撑风险，不在本次范围。

### 5. 计算/状态/环境分离（§5）— 已具备，无需改动

- 计算与状态分离：会话状态在 MySQL（`agent_message`），引擎进程无状态，重启可恢复（已有 WIP 行增量落库与后台任务对账）。
- 计算与执行环境分离：Agent Loop 跑在后端，执行环境在 VPS 沙箱，经 SSH 按需访问（`SandboxSession`）。这与文章"把执行环境留在原地、云端只跑 Loop"是同一思路。

### 6. Watchdog（§6）— 部分具备

已有委派墙钟预算（`Subagents.runWithBudget`，超时中断执行线程并把任务收尾为失败）与后台任务终态对账。文章更进一步的是"由模型判断 Worker 是否有实质进展、是否偏离目标"——这需要额外的模型调用成本，当前规模下不划算，列为远期项。

## 落地清单

| 文件 | 改动 |
|---|---|
| `framework/zagent/history/ConversationHydrator.java` | `member` 块 → 带署名 reminder；新增 `memberHistory`（成员ID → 历次发言） |
| `framework/zagent/subagent/MemberLauncher.java` | `MemberPlan` 增加 `threadContext`（保留旧构造器） |
| `framework/zagent/subagent/Subagents.java` | 成员系统提示注入 `threadContext` |
| `framework/zagent/tools/contract/ToolsetFactory.java` | `Read` 纳入白名单；新增 `agentDescription` 参数透传 |
| `framework/zagent/tools/delegation/AgentTool.java` | 新增描述覆盖构造器 |
| `bot/agent/GroupPrompts.java` | `ORCHESTRATOR_TOOLS` 白名单；提示词分层改写；`agentToolDescription` |
| `bot/agent/GroupCoordinator.java` | 成员线程归档 / 合并 / 截断；`members()` |
| `bot/agent/MioBot.java` | 水合并入成员线程、轮内产出回写、白名单与描述接线、附件提示按角色分流 |
| `bot/service/ContextUsageService.java` | 容量估算与真实轮次同口径（群聊不虚报环境工具占用） |

**前端零改动**（`git status` 无 `MioAIFrontend` 条目）。

## 验证

单元测试（新增 14 个，全通过）：

- `ConversationHydratorMemberTest`（4）：成员块进历史、按成员归档、空/失败不注入、与工具块时间序不冲突
- `GroupCoordinatorThreadTest`（5）：首轮无上下文、跨轮历史可达、同轮可达、不串成员、超限保留最近
- `GroupOrchestratorToolsTest`（5）：环境工具被摘除、分派工具保留、普通会话不受影响、只读档案拿不到写工具、描述只列成员

真实后端端到端（本机 8081 + 真实模型，群 44「演示协作群」）：

- 委派成功，SSE 出 `member_start`，不出现 `tool:"Agent"` 行
- 两个连续轮次：第 2 轮思考链明确引用第 1 轮译文（`"Persistence means victory."`），证明跨轮成员历史回流生效
- 群聊与普通智能体的 `systemTools` 占用实测分离：2156 vs 3878 tokens

全量测试 128 个：123 通过、2 跳过、5 失败。5 个失败均为**既有环境依赖**（`R2UtilTest` 缺 classpath 测试资源、`DocumentControllerTest` 依赖的外部 embedding 渠道 503），与本次改动无关——改动前在基线上同样失败。
