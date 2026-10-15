# MioAI 项目约定

## Git 远程与推送

- 远程命名：
  - `origin` → GitHub：https://github.com/Takina610/MioAI.git
  - `gitee` → Gitee：https://gitee.com/takina610/mio-ai.git
- 本地分支只有 `master`：gitee 照常推 master；GitHub 的分支是 `main`，推送时由本地 master 转换过去（已配置 `remote.origin.push = master:main`，`git push origin` 自动完成转换）。两个远程都要推：

```bash
git push gitee master
git push origin        # 等价于 git push origin master:main
```

## 一键启动/停止脚本编码（红线）

- 根目录 `一键启动.bat`/`一键停止.bat` 与 `一键启动脚本/*.bat` 必须是 **GBK 编码 + CRLF 行尾**：中文 Windows 的 cmd 以 cp936 解析批处理，用 UTF-8 保存会把注释字节读坏成「锟斤拷」，REM 行被拆断后产生「不是内部或外部命令」报错（2026-10-15 实际发生过）。编辑这些文件后必须确认保存编码仍是 GBK。
- 每个脚本头部保留 `chcp 936 >nul`；`chcp 65001` 在 Win11 cmd 下不可靠（chcp 后的预读块仍按旧代码页解码，紧随其后的中文行照样乱码）。
- 例外：`一键启动脚本/stop-all.ps1` 必须是 **UTF-8 with BOM**（PowerShell 5.1 靠 BOM 识别编码）。
- `env.bat` 的 `set` 值全部为纯 ASCII；中文只允许出现在 REM 注释与 echo 文案里。

## 前端 UI 约定

### 布局

- 没有联动关系的内容禁止左右分栏：侧栏必然一边空洞、一边挤压（用户实测否决过两次）。默认纵向流：每个区块占满全宽，从上往下排；次要内容用自适应网格（`repeat(auto-fit, minmax(420px, 1fr))`）并排，卡片 `align-items: start` 贴合内容高度。
- 评分、相关度等决策信息放每行行首作视觉锚点，不放行尾——读者视线从左往右扫，行尾信息要"转头看"。
- 左轨+右结果的分栏只适用于两侧真正联动的情况（左是输入、右随输入变化）。

### 文案（对齐全局指令，红线）

- 不写空态引导句（如"输入内容，测试知识库能召回什么"）、建议式尾缀（如"试试降低阈值"）、与字段标签重复的 placeholder、常驻说明文字。空态要么不渲染，要么只留裸状态句（"未命中任何内容"）。
- 页面只出现：数据、状态读数、控件标签、提交前确认、失败反馈。

### 交互

- 长任务（向量化、批量解析等 SSE/轮询任务）必须同时满足：实时进度反馈（进度条+当前处理项，SSE progress 事件不能丢弃）、路由离开确认（告知任务后台继续+如何恢复）、`beforeunload` 关页守卫。只给按钮转圈不算反馈。
- 控件限制优先于文字说明：能用 maxlength/minmax/disabled 表达的规则不写成文字。
- 列表卡片操作用 hover 浮现（`.dash-card .card-actions` 公共样式：绝对定位 + hover 显现 + footer 淡出），对齐智能体、MCP、技能、知识库列表；不要改成常显覆盖（用户 2026-10-12 拍板）。DOM 顺序必须是 **card-footer 在前、card-actions 在后（最后一个子元素）**——footer 排在 actions 之后会拦截浮动按钮的命中（技能页实测按钮点击失效），后加卡片内容时注意保持该顺序。

### 视觉

- 颜色只用 `styles/variables.scss` 既有 token（暖米底 + 主青绿 `#2aa1a9`），不引入新色相；强度分级用主色透明度，不用 antd 红绿灯配色。
- 状态用圆点+文字（处理中加脉冲动画），不用彩色 tag 堆砌。
- 全站滚动条由 global.scss 的 `*` 选择器统一成自定义细滚动条（暖灰 #d2cfc5）：新滚动容器直接 `overflow: auto` 即可，不要再写 thin-scrollbar mixin 或隐藏滚动条；ChatSessionList 的悬浮浮现样式是唯一例外。

