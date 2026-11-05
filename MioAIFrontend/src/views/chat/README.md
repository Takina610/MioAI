# 对话页（src/views/chat）

站点最复杂的一页：SSE 流式、群聊、附件、动态 UI 块、工具过程展示都在这里。
复杂度靠"按维度切 composable + 纯逻辑下沉 lib"控制，`index.vue` 只做装配。

## 目录职责

```
chat/
├── index.vue                  四相位外壳与各 composable 接线；不放业务实现
├── lib/                       纯函数，无 Vue/网络依赖，配套同名 .test.ts
│   ├── attachmentUtils.ts     附件类型判定与大小格式化
│   ├── refMarkers.ts          @引用标记的解析与还原
│   ├── tokenDom.ts            流式 token 的 DOM 拼接
│   ├── toolDisplay.ts         工具调用 → 展示行
│   └── subagentTranscript.ts  子代理过程转写
├── composables/
│   ├── stream/                SSE 接收、事件分发、智能体活动状态
│   ├── session/               会话列表、外壳相位、左轨状态
│   ├── message/               消息表、重发/编辑、情绪、问候语
│   ├── editor/                编辑器、@菜单、模型选择、上下文用量
│   └── attachment/            上传队列、卡片进出场动画
└── components/
    ├── layout/                左轨、会话侧栏、会话列表
    ├── input/                 输入框与附件卡片
    ├── messages/              消息列表与单条回答的各个块
    ├── dialogs/               个性设置、搜索、群聊创建/编辑
    └── ui/                    动态 UI 块渲染（见下）
```

## 五个 composable 域

一个域对应一类状态；跨域协作通过参数注入，不互相 import 内部实现。

| 域 | 管什么 | 典型入口 |
|---|---|---|
| `stream` | 一条流的生命周期：建连、收事件、断流重连、结束 | `useChatStream` |
| `session` | 会话属于谁：列表、当前会话、外壳开合 | `useChatSessions` / `useChatShell` |
| `message` | 消息属于谁：消息表读写、重发与编辑 | `useChatMessages` / `useMessageActions` |
| `editor` | 用户输入：文本、@资源、模型与档位 | `useChatEditor` / `useModelPicker` |
| `attachment` | 附件：上传队列与卡片动画 | `useChatAttachments` |

## 动态 UI 块 `components/ui`

模型按协议产出 `blocks` 数组，`UiBlockRenderer.vue` 按白名单分发；未知类型降级为可折叠原始数据。

| 子目录 | 块 |
|---|---|
| `container/` | 卡片、折叠、引用 |
| `data/` | 表格、图表、指标、时间线、任务、状态 |
| `media/` | 图片、图集、图注（共用 `ImageSrcLine`） |
| `interactive/` | 表单、选项、操作 |
| `text/` | 数学公式 |

新增块：在对应子目录建组件 → 在 `UiBlockRenderer.vue` 注册 → 后端 `bot/agent/ui/UiBlockValidator` 补校验与白名单。

## 放置规则

- 只给本页用的组件放 `components/`，只给本页用的逻辑放 `composables/<域>/`。
- 不依赖 Vue 的计算放 `lib/`，必须配 `.test.ts`（`npm test` 只跑 `src/**/*.test.ts`）。
- 跨页复用才提升到 `src/components`、`src/composables`。
- 跨目录引用走 `@/`；同目录同级用相对路径。
