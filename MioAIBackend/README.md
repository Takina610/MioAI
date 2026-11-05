# 后端 `com.mio.ai`

依赖方向：`common → system → framework → agent → admin`。业务代码集中在 `agent`，
`admin` 只放启动入口与管理后台。

## 顶层分包

| 包 | 内容 |
|---|---|
| `common` | 统一响应、异常、AOP、过滤器、工具类 |
| `user` | 用户实体、登录拦截、角色 |
| `framework` | 智能体引擎、RAG、MCP、沙箱、记忆 |
| `bot` | 对话运行时：引擎装配、SSE、附件、图片检索 |
| `resource` | 资源域：智能体、知识库、MCP、技能、日志 |
| `admin` | 管理后台入口与接口 |

## 规则

**按域分包，域内分层。** 新增业务先确定域，再放到 `controller / service / mapper / model`：

```
resource/
├── controller/{agent,knowledge,mcp,skill,log}/
├── service/{agent,knowledge,mcp,skill,log}/
│   └── impl/            接口实现
├── mapper/{agent,knowledge,mcp,skill,log,task}/
└── model/
    ├── entity/{agent,knowledge,mcp,skill,log}/   表映射
    ├── dto/{agent,knowledge,mcp,skill,log}/      入参
    ├── vo/{agent,knowledge,mcp,skill,log}/       出参
    └── enums/{agent,knowledge,mcp,skill,log}/    枚举
```

- 包名全小写，不使用 `common.common`、`aop.Aspect` 这类重复或大写的段。
- 目录名必须与 `package` 声明一致（否则编译期报错）。
- `controller` 只做参数校验与转发；业务逻辑在 `service`。
- 跨域引用用显式 `import`，不要留下同包才成立的隐式引用——分包时会被打断。
- 工具类放 `common/utils`，不要在各模块重复实现。

## 测试

`mio-admin/src/test/java` 是唯一测试模块，包路径与被测类保持一致，
这样测试能访问包级私有成员。只测纯逻辑与可离线构造的对象；
依赖 Spring 上下文与外部服务的测试需在无凭据环境下可跳过。
