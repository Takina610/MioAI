# 工具函数（src/utils）

按用途分目录，一个文件一类职责。业务概念不要放这里——放 `src/api` 或页面的 `lib/`。

```
utils/
├── http/request.ts     axios 实例：地址拼接、鉴权头、统一错误提示、401 事件
├── storage/storage.ts  token / 用户信息的本地持久化
├── ui/                 antd 交互封装
│   ├── confirm.ts      二次确认（返回 Promise<boolean>）
│   ├── message.ts      全局提示
│   └── modalLeaveGuard.ts  弹窗关闭收尾兜底（后台标签页动画不触发时兜底）
├── format/format.ts    日期、文件大小、文本截断
├── markdown/           解析与流式增量渲染
└── model/              模型预设与供应商模板
```

## 约定

- 纯函数优先，不做模块级可变状态。
- 同一类能力只保留一份实现，页面里不要复制粘贴。
- 新增目录前先确认现有目录装不下；装得下就加文件。
