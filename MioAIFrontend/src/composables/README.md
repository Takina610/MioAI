# 共享组合式函数（src/composables）

按领域分组，只放**跨页面复用**的状态与副作用逻辑。页面私有的逻辑放
`views/<页面>/composables/`，跟页面一起演进。

## 目录职责

```
composables/
├── admin/useAdminTable.ts          管理后台分页表格状态机（筛选回第一页、翻页续查、统一报错）
├── charts/useEChart.ts             echarts 实例生命周期（初始化/渲染/事件/resize/销毁）
├── chat/chatHandoff.ts             首页 → 对话页的一次性消息交接（模块级单例，非 hook）
├── layout/useFloatingSidebar.ts    悬浮胶囊侧栏状态机（桌面 peek / 移动端抽屉共用）
└── model/
    ├── useModelCatalog.ts          模型目录与当前选择（对话页、首页、个人中心共享内存态）
    └── useReasoningEfforts.ts      思考档位解析与本地记忆
```

## 放置规则

- 多个页面使用 → 按领域放 `composables/<领域>/`；领域目录不存在就新建。
- 只在单个页面使用 → `views/<页面>/composables/`。
- 文件名以 `use` 开头表示 Vue hook；模块级单例/纯函数（如 `chatHandoff`）也放领域目录，
  但要在文件头说明它不是 hook。
- 跨目录引用一律走 `@/` 别名，不用 `../` 跨层。
