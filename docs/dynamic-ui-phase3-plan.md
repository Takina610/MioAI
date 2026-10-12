# 动态 UI 第三期方案：组件库扩展 / 上下文更新 / 图片图文混排

> 对应总路线第十、十一、十二条。前两期（22ee991→a3a03c4）已落地 7 类块（cards/chart/table/tasks/form/actions/status）+
> render_ui 工具 + ui_block SSE + 白名单渲染 + 意图助推。本期在其协议上扩展，不动既有块的语义。

---

## 十、组件库优先级评估

评估口径：① 聊天场景真实出现频率 ② 与既有块的能力重叠度 ③ 实现成本（前后端+提示词+测试）④ 弱模型（deepseek-v4.1-flash）能否稳定产出该结构。

### 本期实现（9 项 + chart 增强）

| 组件 | kind | 结论依据 |
|---|---|---|
| Image | `image` | 十二硬需求；实体介绍配图 |
| ImageList | `image_list` | 十二硬需求；多实体图文行（角色名单/产品清单） |
| ImageGallery | `image_gallery` | 十二硬需求；景点/作品图集 |
| Figure | `figure` | 十二硬需求；教程/长文插图说明块 |
| Timeline | `timeline` | 事件史/版本演进/日程高频；status 结构复用成本低 |
| Quote | `quote` | 名言/文档原文引用；成本极低 |
| Metric | `metric` | KPI 数字条；cards 的紧凑变体，数据类回答高频 |
| Choice | `choice` | 快速二选一/多选一续聊；actions 的单选语义变体 |
| chart 散点 | `chartType=scatter` | echarts 原生支持，一行分支 |

RichContent：**不新增**。既有 answerSegments 机制已实现"一条消息混合 text+任意已注册 ui 块、模型决定顺序"，再加一层包装块属于重复建模。

### 二期候选（记录规格，暂不实装）

| 组件 | 暂缓理由 | 启用条件 |
|---|---|---|
| Accordion | 长答案折叠有价值，但前端成本中等，出现频率一般 | 长教程场景多起来后 |
| Feedback | 点赞点踩需要后端存储表+聚合口径，本期无消费方 | 有运营分析需求时 |
| Math (KaTeX) | 需引入 KaTeX 依赖；数学问答占比待观察 | 数学场景高频时 |
| Flowchart (Mermaid) | mermaid 体积大（~1MB），且弱模型产出合法 mermaid 语法不可靠 | 换强模型或做语法校验降级后 |
| Confirm | 语义上并入 actions（confirm 字段），独立块无增量 | 出现真实 op 白名单操作时 |
| Log | 无产生日志流的业务场景 | Agent 长任务日志面板立项时 |

### 不做（重叠/低频/重依赖）

- **Comparison / Progress / Steps / ToolResult**：cards+table / status 块 / 工具行已分别覆盖。
- **Code**：Markdown 代码块外壳已做（含 hljs），独立块无增量。
- **Wizard / Upload / Editor**：form + 附件上传链路已覆盖；向导式多步表单聊天场景低频。
- **Approval**：OP_WHITELIST 为空集（无真实业务操作可审批），实装即空壳。
- **Network / Calendar / Map / Document / Terminal / DiffViewer / MediaPlayer**：重前端依赖（地图供应商/文档渲染/终端模拟），聊天场景低频，ROI 不足。

### 已实现组件规格（7 点）

以下字段全部进入 render_ui inputSchema + 后端语义校验 + 前端白名单。通用约束：文本字段 200 字截断（TEXT_CAP）；数组项超上限截断不报错；坏块丢弃并在工具回执中说明，模型自纠重调。

**image**：`{kind, title?, image:{url!, thumbnailUrl?, title?, alt?, caption?, sourceUrl?, sourceName?, author?, license?, width?, height?}, ratio?("auto"|"16/9"|"4/3"|"1/1"|"3/4"), fit?("cover"|"contain")}`
- 必需：image.url；其余可选。默认 ratio=auto（按 width/height 推断）、fit=cover。
- 场景：单实体配图（一个角色/一台设备）。不适用：多实体（用 image_list）、纯风景集（用 gallery）。
- 交互：antd Image 点击预览原图；来源链接新窗打开（rel=noopener）。
- 前后端：url 必须来自本会话 image_search 结果登记簿（防伪造，见十二）；前端只渲染 https://；加载失败占位（标题+来源，不隐藏消息）。
- 状态：加载中懒加载占位 → 失败占位 → 正常；alt 兜底为 title。

**image_list**：`{kind, title?, items:[{url!, thumbnailUrl?, title!, desc?, sourceUrl?, author?, license?}] ×≤8}` —— 图文行（图左文右，同一实体联动）；人物名单/搜索结果/产品清单。

**image_gallery**：`{kind, title?, images:[{url!, thumbnailUrl?, title?}] ×≤12}` —— 网格 + PreviewGroup 点击翻看大图；≤4 张时纯网格不轮播。

**figure**：`{kind, title?, image:{同上}, caption!}` —— 教程/知识长文插图块：图 + 说明文字 + 来源行，article 风格。

**timeline**：`{kind, title?, events:[{time?, title!, desc?}] ×≤12}` —— 竖向时间线（圆点+时间+标题+描述）；事件演进/日程/发展史。不适用：带执行状态的流程（用 status）。

**quote**：`{kind, text!, author?, source?}` —— 引用块 + 署名行。

**metric**：`{kind, title?, items:[{label!, value!, unit?, trend?("up"|"down"|"flat")}] ×≤4}` —— 紧凑 KPI 数字条（大数字+标签+趋势箭头）。不适用：每项需要长描述（用 cards）。

**choice**：`{kind, title?(题干), options:[{label!, prompt?}] ×≤6}` —— 单选胶囊组；点击即发送 prompt（缺省发 label）。与 actions 区别：choice 表达"请用户选一个方向"，actions 表达"预测的下一步操作"；choice 不沉底（inline），actions 沉底。

**chart 散点**：chartType 枚举增加 `scatter`；坐标系 value×value，其余复用。

---

## 十一、上下文更新机制

### 四类操作的落点

| 操作类型 | 机制 | 状态 |
|---|---|---|
| 创建新 UI 内容 | render_ui 无 targetId → 新块（现状） | 既有 |
| 更新已有 UI 内容 | render_ui 带 `targetId=ui-xxxxxxxx` → 原位替换 | **本期新增** |
| 更新真实业务状态 | `/bot/ui-state`（tasks 勾选落库）+ 未来 op 白名单 | 既有 |
| 请求用户确认后执行 | actions 按钮 + OP_WHITELIST 空集安全边界（未授权 op 整按钮丢弃） | 既有 |

### targetId 原位替换协议

1. **块 id 对模型可见**：render_ui 工具回执从 "Rendered 2 UI block(s)" 升级为带 id 明细：`Rendered 2 new block(s): cards ui-a1b2c3d4, table ui-e5f6a7b8`。回执随 tool 块持久化并水合进后续轮次上下文——模型始终知道历史块的 id。
2. **替换语义**（BotEventChannel.uiBlockReplace）：
   - 目标块在本轮 displayBlocks 内（同轮重复调整）→ 数组原位替换，随 WIP 增量落库；
   - 目标块在历史行 → 按 id 定位 agent_message 行，整行 blocks 原位替换后写回（与 /bot/ui-state 同一写回路径），**不新增消息**；
   - SSE 事件带 `update:true` 标记，前端跨消息按 id 寻址替换（useChatStream 内扫全部消息）；寻址失败降级为当条消息追加（不丢内容）。
   - 替换后块 id 不变（稳定寻址锚），历史重载读到的是已更新版本。
3. **防重复创建**：提示词明确"修改早前渲染过的块 → 带 targetId 原位更新，不要重复渲染新块"；回执附 id 也让模型无需猜。
4. **上下文一致性**：水合器回放的是 tool 块（旧 args + 本轮 targetId 调用），模型可见完整变更链，与 ui-state 勾选的既有口径一致。
5. **安全**：targetId 校验格式 `^ui-[0-9a-f]{8}$` 且必须存在于本会话消息中，不存在则丢弃并回执告知（防跨会话/伪造寻址）；会话所有权由既有链路保证（render_ui 只在本人会话的轮次内执行）。

---

## 十二、图片检索与图文混排

### 架构（职责划分）

```
模型 ──image_search(query, category?)──▶ ImageSearchService ──▶ Provider 抽象
                                            │                     ├─ OpenverseProvider（通用：CC 图片）
                                            │                     └─ AniListProvider（动漫角色/作品）
                                            ▼
                                   URL 登记簿（Redis, 按 chatId, TTL 7d）
模型 ──render_ui(image*/figure 块)──▶ UiBlockValidator 校验：
                                        url ∈ 登记簿？https？→ 否则丢弃+回执
                                            ▼
                                   ui_block SSE → 前端白名单组件渲染（懒加载/失败占位/来源信息/预览）
```

- **模型职责**：判断要不要图、出检索词、从工具结果里挑候选、决定图的位置与块形态、写说明文字。**不负责也不允许**编造 URL/作者/授权。
- **后端职责**：真实调用图片 API、超时/异常/限流处理、结果规范化、登记簿维护、render_ui 图片块的 URL 防伪造校验。MVP **不在服务端抓取远程图片**（前端 img 直连展示），不引入 SSRF 面；如未来要落盘再走独立下载服务（URL/重定向/内网/类型/大小/超时全限）。
- **前端职责**：白名单组件渲染、懒加载、失败占位、来源/作者/授权展示、预览大图、https-only。

### 图片 API 实测记录（2026-10-12，真实调用）

**Openverse** `GET https://api.openverse.org/v1/images/?q=...&page_size=n`
- 匿名可用，限流 20/min、200/day（响应头 x-ratelimit-*），个人使用足够；
- 字段：id / title / url（原图）/ thumbnail（官方托管缩略图）/ foreign_landing_url（来源页）/ creator / creator_url / license（如 by-nc-nd）/ license_version / license_url / attribution / width / height / provider / mature / tags；
- 授权真实：CC 系列许可逐图携带，前端照实展示，不做商用承诺。

**AniList** `POST https://graphql.anilist.co`（免 key）
- 已验证查询：`Page(perPage:n){characters(search:$s){id name{full native} image{large} description media(perPage:1){nodes{title{romaji}}}}} Media(search:$s,type:ANIME){title{romaji} coverImage{extraLarge}}`；
- 角色返回真实立绘（s4.anilist.co CDN）+ 简介 + 出自作品；作品封面可作为图集补充；
- 无授权字段 → license/author 留空，来源标注 AniList，不虚构授权信息。

**Pexels**：application.yml 已有 `pexels.search.*` 配置残根（无代码、无 key）→ 不实装，作为未来 Provider 扩展位（申请 key 后按同接口接入）。

**WebSearch 现状核查**：仅返回 title+url（无图片字段）→ 确认需要独立 image_search 工具，不能假设文本搜索带图。WebFetch 保持通用抓取，og:image 提取作为后续增强（不阻塞 MVP）。

### 统一图片数据结构（ImageResult）

`id / imageUrl / thumbnailUrl / sourceUrl / sourceName / title / author / license / width / height`
缺失字段为空，不编造。渲染层 alt=title，caption 由模型结合上下文书写。

### 工具与提示词

- **image_search 工具**（MioBot 主轮次注册，与 render_ui 同级）：入参 `query`（必需）+ `category`（general|anime，缺省 general；general 零结果时自动回退 anime）；返回编号候选列表（含全部 URL 字段），模型原样引用。超时/失败返回"服务不可用，正常无图作答"——聊天不因图片服务挂掉而挂掉。
- **UI_RENDERING 新增两节**：
  - 图片决策：明确实体（角色/真人/景点/产品/硬件）→ 先 image_search 再 image/image_list/gallery/figure，URL 必须原样来自工具结果（否则块被丢弃）；概念/代码/翻译/纯问答 → 不搜图；结果不相关 → 弃用不硬塞；图文按语义就近混排，不堆尾部。
  - 块更新：修改既有块带 targetId 原位替换，避免重复组件。
- **意图助推**：MioBot 新增图片意图模式（动漫/角色/人物/景点/旅游/外观/照片…）命中注入轮次提醒（可配图场景提示 image_search 链路）；"纯文字/不要图"既有红线优先。

### 安全与可靠性清单

- URL 防伪造：image 块的 url/thumbnailUrl 必须在本会话登记簿内 → 模型输出任意 URL 绕不过校验（对应验收第 10 条）；
- 协议：仅 https（Provider 规范化 + 校验 + 前端三重）；
- 无服务端抓图 → SSRF/重定向/资源消耗风险面为零（未来落盘另立下载服务再限）；
- 授权展示：license/author 照实展示；无授权信息不声称可自由商用；Openverse 图随 attribution 字段携带署名链；
- 失败降级：图片加载失败 → 该图占位（不吞整条消息）；搜索无结果/超时 → 纯文字正常作答；历史会话重载 → blocks 落库机制原样还原（外链 URL 持久有效，死链走占位）。

### 验收场景 → 测试方法

1. 动漫角色介绍 → image_search(anime) + image_list + 人物介绍（smoke 场景 A）
2. 旅游目的地 → 图 + 地点卡 + 行程建议（smoke 场景 B）
3. Redis 概念 → 不调 image_search、零图片块（结构化块照常允许）（smoke 场景 C）
4. 显式纯文字 → 零 render_ui、零图片（smoke 场景 D）
5. 无关结果不硬塞 → 提示词红线 + 场景 C 侧面验证（Openverse 弱相关词）
6. 图片服务不可用 → `image.search.enabled=false` 重启跑场景 B，聊天正常纯文字
7. 来源/作者/授权展示 → 前端组件渲染断言（浏览器目检）
8. 历史重载还原 → GET /bot/messages 断言图片块在库 + 前端重开会话目检
9. 混合编排 → smoke 断言同一消息含 text+图片块+cards/chart
10. URL 防伪造 → 诱导模型直接贴图 URL，断言产物块内所有 url ∈ 登记簿（非法块被丢弃）

---

## 实施清单

后端（mio-agent，新增 7 文件 + 改 6 文件）：
- `bot/service/image/`：ImageResult、ImageSearchProvider、OpenverseProvider、AniListProvider、ImageUrlRegistry、ImageSearchService
- `bot/agent/ImageSearchTool.java`；UiRenderTool 拆分出的 UiBlockValidator / UiExtendedBlockValidator / UiImageBlockValidator
- 改：UiRenderTool（targetId+回执 id+新 kind 装载）、BotEventChannel（uiBlock 返回 id + uiBlockReplace）、MioBot（注册+构造注入）、MioBotController（构造传参）、SystemPrompts（UI_RENDERING 扩展）

前端（新增 9 文件 + 改 4 文件）：
- `views/chat/components/ui/`：UiImage / UiImageList / UiImageGallery / UiFigure / UiTimeline / UiQuote / UiMetric / UiChoice + ImageSrcLine（来源行复用）
- 改：UiBlockRenderer（白名单）、UiChart（scatter）、types/chat.ts、useChatStream（update 块跨消息替换）

配置：`image.search.enabled`（默认 true）、`image.search.openverse-url`、`image.search.anilist-url`（均有默认值，yml 零必改项）。

测试：RunKit/smoke_image_ui.py（登录→SSE→断言工具调用/块产物/登记簿/落库回读）+ 浏览器目检 + vue-tsc + mvn clean package 重启冒烟。

---

## 实施后记（实测结论与坑，2026-10-12 深夜收尾）

**登记簿升级为资产簿**：首版只存 URL 白名单，但弱模型不会手抄 sourceUrl/author/license → 来源信息行不出现。
终版 `ImageUrlRegistry` 存完整 ImageResult JSON，校验命中后**自动回填**来源/作者/授权/尺寸（模型字段优先，
缺项由真实检索结果补齐）——验收第 7 条不再依赖模型自觉，实测 figure 块带出 `来源 AniList` 链接。

**AniList 查询姿势**：`Page.characters(search:$s)` 是角色名精确匹配，整句/作品名查询必然空结果
（"One Piece characters" → 0 命中）。终版双根查询：`Page(perPage:4){characters(search:$s)}`（问"某角色"）
+ `Media(search:$s,type:ANIME){characters(perPage:8,sort:ROLE)}`（问"某作品的主要角色"，按戏份排序）+ 作品封面补充。
工具描述同时强调 query 必须是**短实体名**（"One Piece" 而非句子）。

**弱模型（deepseek-v4.1-flash）实测调参**：
- 图片块要**字面量 JSON few-shot**（抽象描述"image_list: one row per character"不触发，给出真实 JSON 形状后稳定产出）；
- 意图助推 reminder 加"搜索后必须同轮用图片块渲染，文本-only 收尾即失败"的硬约束（此前搜完图还是只出 cards/table）；
- 更新语义加动词触发清单（改成/换成/调整/修改/更新）+ targetId 字面量示例后，原位更新成功率明显提升（仍非 100%，弱模型行为抖动）；
- image_search 调用次数从 10 次（逐角色搜索）收敛到 1-3 次（提示"搜整组不搜个体"）。

**验收结果**（smoke_image_ui.py 全场景 + 浏览器目检）：
- A 海贼王角色：image_search(anime) + image_list 真实立绘 + cards/table/actions 混排 + 落库回读 ✓（浏览器目检：四行图文、真实图片、来源行）
- B 京都旅游：图 + 行程 tasks + cards 混排 ✓
- C Redis 概念：零 image_search、零图片块（结构化块照常）✓
- D 纯文字红线：零 render_ui / 零 image_search ✓
- E targetId 原位更新：同 id 替换、落库无重复块 ✓（多轮复测通过）
- F 伪造 URL 拦截：模型实弹贴假 URL → 登记簿校验丢弃 + 回执自纠、零非法块 ✓
- G 历史回放：重开会话图片块原样还原 ✓
- 图片服务关闭（enabled=false）：聊天正常、模型明说"不配图"纯文字降级 ✓
- vue-tsc 0 错；mvn clean package 通过

**运维坑（本次排查 401 Invalid credential 的根因链）**：
`env.bat` 会加载**项目根 `.env`**（第 36-38 行），其中的 `DASHSCOPE_API_KEY` 若与注册表用户变量不同步
（本次为 116 字符旧 key），会经 env.bat 第 79 行 `if defined` 覆盖正确值 → JVM 发旧 key → zen 网关 401。
修复：`.env` 的 DASHSCOPE_API_KEY 已同步为注册表当前值。以后换 key 记得**两处同步**（注册表 User 域 + 项目根 .env），
或直接删 .env 中该行让注册表值生效。诊断手段：本地回显服务器抓 Authorization 头 / curl 复刻 zen 请求形态对比。
