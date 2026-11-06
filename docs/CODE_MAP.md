# 代码地图

按"我要改什么"索引到具体目录。路径都以仓库根为起点。

## 后端 `MioAIBackend/`

多模块，依赖方向 `common → system → framework → agent → admin`，业务代码集中在 `mio-agent`。

### 请求入口

| 想找 | 位置 |
|---|---|
| 启动类、`application.yml` | `mio-admin/src/main/java/com/mio/ai/MioAIApplication.java`、`mio-admin/src/main/resources/` |
| 对话页接口（SSE、附件、续连、标题） | `mio-agent/.../bot/controller/MioBotController.java` |
| 对话内建资源（工具注册与开关） | `mio-agent/.../bot/agent/MioBot.java`（`setResourceAdmin`）、`bot/agent/resource/` |
| 智能体 CRUD、群聊、版本 | `mio-agent/.../resource/controller/agent/` |
| 知识库、文档、命中测试 | `mio-agent/.../resource/controller/knowledge/` |
| MCP 工具 CRUD 与在线校验 | `mio-agent/.../resource/controller/mcp/` |
| 技能导入、广场 | `mio-agent/.../resource/controller/skill/` |
| 用量、用户洞察、各类日志 | `mio-agent/.../resource/controller/log/` |
| 用户注册登录 | `mio-system/.../user/controller/` |
| 管理后台 | `mio-admin/.../admin/controller/` |

> 包路径前缀 `com/mio/ai/` 省略。控制器只做参数校验与转发，业务在对应 `service/` 包。

### 业务实现 `mio-agent`

```
com.mio.ai
├── bot/                        对话运行时
│   ├── agent/                  引擎装配与主循环（MioBot、群聊调度、事件通道）
│   │   ├── resource/           对话内资源管理工具（resource_list / agent_save / group_create / agent_bind）
│   │   └── ui/                 动态 UI 块的产物校验与 render_ui 工具（含预览卡片 agent_card）
│   ├── controller/             HTTP 入口（SSE、附件代理、摘要、限流）
│   ├── mapper/ entity/ vo/     MyBatis-Plus 数据访问与传输对象
│   ├── repository/             会话历史读写
│   ├── service/                附件、上下文用量、模型连通性、技能物化
│   │   ├── image/              图片检索（AniList / Openverse）
│   │   └── resource/           资源管理工具的落地层（鉴权、清洗、名字解析、图标规范化、绑定去重）
│   └── util/                   SSE 封装、标题生成
└── resource/                   资源域
    ├── controller/{agent,knowledge,mcp,skill,log}/   按域分包的入口
    ├── service/                                       按域分包，impl/ 放实现
    │   └── log/analytics/      用量分析聚合（行列装载、下钻、流向）
    ├── mapper/{agent,knowledge,mcp,skill,log,task}/
    └── model/
        ├── entity/{agent,knowledge,mcp,skill,log}/    表映射，按域分目录
        ├── dto/{agent,knowledge,mcp,skill,log}/       入参
        ├── vo/{agent,knowledge,mcp,skill,log}/        出参
        └── enums/{agent,knowledge,mcp,skill,log}/     状态枚举
```

### 引擎与基础设施 `mio-framework`

```
com.mio.ai.framework
├── zagent/                     智能体引擎（与 Spring AI 解耦的一层）
│   ├── AgentEngine.java        主循环：模型流 → 工具调用 → 结果回灌
│   ├── compact/                上下文压缩
│   ├── context/                提示词与上下文拼装
│   ├── history/                会话状态、历史投影
│   ├── hooks/ memory/ reminder/
│   ├── stream/                 流式事件收集与失败分类
│   ├── subagent/               子代理与群成员派发
│   ├── task/                   后台任务（脱离式 bash）
│   ├── tools/                  工具层
│   │   ├── contract/           工具契约：ToolEntry、ToolRegistry、ToolScheduler、ToolsetFactory
│   │   ├── fs/                 文件与命令：Read/Write/Edit/Bash/Glob/Grep + SandboxFs
│   │   ├── delegation/         委派：Agent、AskUserQuestion、TaskOutput/Stop、Todo
│   │   ├── web/                WebFetch / WebSearch
│   │   └── support/            描述文案与参数解析
│   └── vendor/                 各模型供应商的流式适配
├── rag/                        检索增强
│   ├── reader/                 PDF / Markdown / TXT / Tika 解析
│   ├── splitter/               分块
│   ├── embedding/              向量化节流
│   ├── query/                  关键词抽取与增强
│   ├── retrieval/              检索配置、结果、分块查询
│   └── rerank/                 重排客户端
├── mcp/                        MCP 客户端、命令解析、配置脱敏
├── sandbox/                    SSH 沙箱会话与文件传输
├── github/ skillssh/           外部技能来源
├── memory/                     会话记忆摘要
└── advisor/ config/            日志切面与 Spring 配置
```

### 公共层

| 模块 | 内容 |
|---|---|
| `mio-common/.../common/api` | `BaseResponse`、`PageRequest`、`FileType` |
| `mio-common/.../common/aop` | `annotation/` 注解、`aspect/` 切面实现 |
| `mio-common/.../common/exception` | 业务异常、错误码、全局处理器 |
| `mio-common/.../common/utils` | IP 解析、出站 URL 校验、对象存储、Redis |
| `mio-system/.../user` | 用户实体、登录拦截、角色 |

## 前端 `MioAIFrontend/src/`

### 页面

| 路由 | 目录 |
|---|---|
| `/` 首页 | `views/home/` |
| `/chat/:id` 对话 | `views/chat/` |
| `/share/:id` 分享 | `views/share/` |
| `/dashboard/*` 控制台 | `views/dashboard/{agent,knowledge,mcp,skill,profile,usage}/` |
| `/admin/*` 管理后台 | `views/admin/` |
| 错误页、图标调试 | `views/error/`、`views/dev/` |

### 对话页 `views/chat/`（最复杂的一页）

```
chat/
├── index.vue              页面装配：四相位外壳 + 各 composable 接线
├── lib/                   纯函数，无 Vue 依赖，可直接单测
│   ├── attachmentUtils.ts 附件类型判定、大小格式化
│   ├── refMarkers.ts      @引用标记的解析与还原
│   ├── tokenDom.ts        流式 token 的 DOM 拼接
│   ├── toolDisplay.ts     工具调用 → 展示行
│   └── subagentTranscript.ts 子代理过程转写
├── composables/
│   ├── stream/            流式会话：SSE 接收、事件分发、智能体活动状态
│   ├── session/           会话维度：会话列表、外壳相位、左轨状态
│   ├── message/           消息维度：消息表、重发/编辑、情绪、问候语
│   ├── editor/            输入维度：编辑器、@菜单、模型选择、上下文用量
│   └── attachment/        附件维度：上传队列、卡片进出场动画
└── components/
    ├── layout/            外壳：左轨、会话侧栏、会话列表
    ├── input/             输入区：输入框、附件卡片
    ├── messages/          消息流：消息列表、单条回答、思考、工具行、提问卡、子代理面板
    ├── dialogs/           弹窗：个性设置、搜索、群聊创建/编辑
    ├── ui/                动态 UI 块渲染
    │   ├── UiBlockRenderer.vue  白名单分发
    │   ├── container/     卡片、折叠、引用
    │   ├── data/          表格、图表、指标、时间线、任务、状态
    │   ├── media/         图片、图集、图注
    │   ├── interactive/   表单、选项、操作
    │   └── text/          数学公式
    └── ContextUsageIndicator.vue  上下文占用环
```

### 数据层

| 目录 | 说明 |
|---|---|
| `api/chat/` | 对话：SSE、附件、会话记忆、消息行 |
| `api/agent/` | 智能体及其知识库/MCP/技能/记忆绑定 |
| `api/{knowledge,mcp,skill,model,usage,user}/` | 各资源入口 |
| `api/admin/` | 管理后台接口，一域一文件 |
| `types/` | 与接口对应的类型；`types/index.ts` 统一再导出 |
| `utils/http/request.ts` | axios 实例、鉴权、错误提示 |
| `utils/model/` | 模型预设与供应商模板 |
| `store/`、`router/` | Pinia 状态、路由与守卫 |

### 跨页面复用

| 目录 | 放入规则 |
|---|---|
| `components/` | 多页面复用的领域组件与展示原语，细则见 `components/README.md` |
| `composables/` | 多页面复用的状态逻辑，按 `admin/charts/chat/layout/model` 分域 |
| `mobile/` | 移动端适配层，新增断点样式改这里，不写进页面 |
| `styles/` | 全局样式与设计变量（颜色只用 `variables.scss` 的 token） |

## 放置约定

新增文件时按"谁的职责"落位，避免再次堆平：

1. **只服务一个页面** → 该页面的 `components/` 或 `composables/`。
2. **多页面复用** → `src/components/<领域>/` 或 `src/composables/<领域>/`。
3. **无 Vue 依赖的纯逻辑** → 就近的 `lib/`，文件名即职责，配套 `.test.ts`。
4. **后端** → 先定域（agent/knowledge/mcp/skill/log/bot），再按 `controller/service/mapper/model` 分层。
5. **单一目录文件数控制在 10 个左右**；超过说明还有第二层职责没拆出来。
