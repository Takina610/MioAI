# 智能体引擎（src/main/java/com/mio/ai/framework/zagent）

与 Spring AI 解耦的一层：主循环、上下文、工具、子代理、后台任务都在这里。
模型差异由 `vendor/` 适配，业务差异由上层 `bot/` 注入。

## 模块

| 目录 | 职责 |
|---|---|
| `AgentEngine.java` | 主循环：模型流 → 工具调用 → 结果回灌 |
| `stream/` | 流式事件收集、失败分类 |
| `context/` | 系统提示与环境探测 |
| `history/` | 会话状态、历史投影 |
| `compact/` | 上下文超限压缩 |
| `tools/` | 工具层（见下） |
| `subagent/` | 子代理与群成员派发 |
| `task/` | 后台任务与脱离式命令 |
| `memory/` | 跨会话记忆 |
| `hooks/` `reminder/` | 生命周期钩子与系统提醒 |
| `vendor/` | 各供应商的流式模型适配 |

## 工具层 `tools/`

```
tools/
├── contract/    工具契约：ToolEntry、ToolRegistry、ToolScheduler、ToolsetFactory、
│                ToolContext、ToolResult、ToolMedia、ToolUseFailure
├── fs/          文件与命令：Read / Write / Edit / Bash / Glob / Grep
│                + SandboxFs（沙箱文件系统）、ReadFileState、ImagePrepare、EditMatcher
├── delegation/  委派：Agent、AskUserQuestion、TaskOutput、TaskStop、Todo
│                + QuestionGate、DelegationExecutors
├── web/         WebFetch / WebSearch
└── support/     工具描述文案（ToolDescriptions）与参数解析（Args）
```

### 新增一个工具

1. 放到对应能力目录（文件、委派、网络；都不沾边再建目录）。
2. 提供 `public static ToolEntry entry()`（有状态工具用构造器 + 实例方法）。
3. 在 `contract/ToolsetFactory` 里注册，明确 `readOnly / concurrentSafe / destructive / timeoutMs`。
4. 跨包使用时，类与入口方法必须是 `public`——同包默认可见性在分包后不成立。
5. 描述文案写进 `support/ToolDescriptions`，不要散落在工具类里。

### 注意

`contract/` 被所有工具依赖，反过来不依赖任何具体工具。新增依赖时保持这个方向，
否则工具层会重新变成一团。
