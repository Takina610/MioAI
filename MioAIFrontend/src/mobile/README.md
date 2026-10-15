# 移动端适配层（src/mobile）

全站移动端适配集中在 `src/mobile/`，不把规则追加进各页面文件。
新增移动端行为时改这里，不再往 `views/**` 的 `<style>` 里写 `@media`。

## 目录职责

```
mobile/
├── breakpoints.ts       断点常量（JS 侧唯一来源，与 _tokens.scss 的 $bp-* 对齐）
├── useIsMobile.ts       视口/触屏检测 → 在 <html> 上挂标记类 + 暴露响应式布尔
├── useShellSidebar.ts   外壳侧栏状态机（桌面悬浮胶囊 ↔ 移动端覆盖式抽屉）
├── MobileScrim.vue      抽屉遮罩（点遮罩关抽屉）
├── components/
│   ├── MobileAgentDrawer.vue  对话页智能体切换抽屉（补位桌面左轨）
│   └── HomeNavDrawer.vue      首页导航抽屉（补位桌面顶栏 nav）
├── index.scss           样式入口（main.ts 引入一次）
└── styles/
    ├── _tokens.scss     断点变量 + 安全区/触控目标/横向滚动 mixin
    ├── base.scss        跨模块共性：触屏兜底、antd 收敛、表格横滚、iOS 聚焦防缩放
    ├── shell.scss       三个外壳（对话/控制台/管理后台）骨架
    ├── chat.scss        对话页
    ├── dashboard.scss   控制台内页
    ├── admin.scss       管理后台内页
    └── pages.scss       首页/分享页/错误页/全局弹窗
```

## 断点与标记类

断点只在 `breakpoints.ts` 定义一次，JS 依据 matchMedia 在 `<html>` 上维护标记类：

| 标记 | 宽度 | 用途 |
|---|---|---|
| `is-compact` | ≤480 | 小屏手机：进一步压缩留白 |
| `is-tight` | ≤640 | 首页大标题/横幅提前折叠 |
| `is-mobile` | ≤768 | **移动外壳**：侧栏抽屉化、内容满宽、表格横滚 |
| `is-tablet` | ≤960 | 首页顶栏 nav 折叠为菜单按钮 |
| `is-narrow` | ≤1200 | 图表/指标栅格收窄 |
| `is-touch` | `(hover: none)` | 触屏：hover 才显形的控件常显 |

样式侧一律以 `html.<标记>` 前缀书写而不写 `@media`，原因见 `_tokens.scss` 顶部说明：
标记类既替代散落的媒体查询字面量，又比组件 scoped 选择器多一档特异性。

## 覆盖层的特异性约定

被覆盖对象是组件 scoped 样式（编译为 `.a .b .c[data-v-hash]`，特异性 4~6 类）与
antd 运行时注入的 CSS-in-JS（顺序不可控）。因此移动层对**桌面已声明过的属性**
统一加 `!important`；纯新增属性（如安全区 padding）不加。

## 三外壳的抽屉机制

`useShellSidebar()` 包一层既有的 `useFloatingSidebar()`：
桌面完全走原路径（常驻展开 + 鼠标悬浮 peek），移动端收敛为覆盖式抽屉。

- 对外接口不变：`collapsed` / `peeking` / `enterHandle` / `leaveHandle` / `toggle`
- 移动端额外得到 `drawerOpen`，用于渲染遮罩与绑定布局类名
- 抽屉展开时锁定 `body` 滚动、Esc 关闭、切路由自动收起

对话页（`views/chat/index.vue`）保留自己的四相位状态机（它还要协调左轨融合），
在移动端把相位语义映射为"会话抽屉开合"，左轨相关逻辑直接短路。
