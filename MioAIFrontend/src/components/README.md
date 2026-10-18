# 共享组件（src/components）

放**不绑定单个页面业务语境**的组件：多个页面复用的领域组件，以及与业务无关的展示原语。
只服务一个页面的组件放该页面的 `views/<页面>/components/`，不要提升到这里。

## 目录职责

```
components/
├── agent/                     智能体身份
│   ├── AgentCreateModal.vue   创建智能体（图标设计 → 名称/描述）
│   ├── GroupAvatar.vue        群聊成员堆叠头像
│   └── bot-icon/              机器人图标渲染与动画引擎
│       ├── BotIcon.vue        图标渲染入口
│       ├── BotIconDesigner.vue 形状/配色选择器
│       ├── BotPet.vue         对话页宠物形态
│       ├── types.ts           配置结构、调色板、序列化与校验
│       └── engine/            几何/姿态/表情/特效等纯计算模块
├── auth/AuthModal.vue         登录/注册弹窗（对话页与控制台共用）
├── charts/BaseDonutChart.vue  环形图通用外壳（echarts）
├── content/MarkdownView.vue   Markdown 流式渲染 + 代码复制
├── icons/                     无业务语义的内联 SVG 图标
└── ui/                        与业务无关的交互/展示原语
    ├── CollapseTransition.vue 高度折叠过渡
    └── RoseFourLoader.vue     玫瑰线加载动画
```

## 放置规则

- 组件依附某个页面的业务语境 → `views/<页面>/components/`。
- 多个页面复用、且能说清所属领域 → `components/<领域>/`；领域目录不存在就新建。
- 纯图标 → `components/icons/`；纯交互原语、不含业务概念 → `components/ui/`。
  这两类是通用库，即使当前只有一处使用也放这里。
- 组件内部引用同一目录的文件用相对路径，跨目录一律走 `@/` 别名。
