# 接口层（src/api）

一域一目录，目录内 `index.ts` 是该域的默认入口；跨域拆文件，不再平铺。

```
api/
├── chat/index.ts           对话：SSE、附件、续连、标题
├── chat/memory.ts          会话列表与置顶/删除
├── chat/messages.ts        消息行与历史版本
├── agent/index.ts          智能体 CRUD、群聊、版本
├── agent/{knowledge,mcp,skill,memory}.ts   智能体与资源绑定
├── knowledge/index.ts      知识库、文档、向量化、命中测试
├── mcp/index.ts            MCP 工具 CRUD 与在线校验
├── skill/index.ts          技能导入（ZIP/GitHub/skills.sh）、广场
├── model/index.ts          模型目录与连通性测试
├── usage/index.ts          用量统计与分析
├── user/index.ts           注册登录、资料、头像
└── admin/                  管理后台，一域一文件
```

## 约定

- 函数只做「拼请求 + 拆响应」，不含业务分支；类型从 `@/types` 取。
- 请求实例统一走 `@/utils/http/request`，不要在文件里另建 axios。
- 新接口按资源归入已有目录；确实不属于任何域时才新建目录，并在本文件补一行。
- 测试里 mock 模块时路径要跟这里的真实路径一致
  （例如 `vi.mock('@/api/chat/messages')`），否则 mock 不生效、会走真实网络。
