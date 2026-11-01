# MioAI 前端

Vue 3 + TypeScript + Vite + Pinia + Ant Design Vue。

```bash
pnpm install
pnpm dev      # http://localhost:3000，/api 代理到 http://127.0.0.1:8081
pnpm build    # 类型检查 + 构建，产物在 dist/
```

## Cloudflare Pages 部署

仓库根目录不是前端，连接 GitHub 仓库后靠 **Root directory** 指定子目录即可，无需调整仓库结构。

Build settings：

| 配置项 | 值 |
| --- | --- |
| Production branch | `main` |
| Root directory | `MioAIFrontend` |
| Build command | `pnpm build` |
| Build output directory | `dist` |

环境变量（Settings → Environment variables）：

| 变量 | 值 |
| --- | --- |
| `NODE_VERSION` | `24` |
| `PNPM_VERSION` | `12.6.0`（可选，仅用于固定构建可复现） |
| `VITE_API_BASE_URL` | 后端公网地址，如 `https://api.example.com` |

- 构建命令与产物目录都相对 Root directory，产物目录写 `dist`，不要写 `MioAIFrontend/dist`。
- `pnpm-workspace.yaml` 必须保留 `packages` 字段（当前为 `packages: []`）：pnpm 9 及更早版本缺这个字段会直接报 `packages field missing or empty` 并中断安装。Pages 构建日志里的 `Detected ... pnpm@X` 会随镜像与锁文件版本变化，实测未设变量时可能落到 pnpm 9，别赌它一定是新版。
- 依赖构建脚本白名单同时写了 `allowBuilds`（pnpm 10+）和 `onlyBuiltDependencies`（pnpm 9 及更早，10/12 也认）。只写前者时 pnpm 9/10 会忽略整段配置，跳过 `@vue-office/*` 等依赖的 postinstall 并打印 `Ignored build scripts` 警告。
- 仓库内 `.env` 的 `VITE_API_BASE_URL=/api` 是给 Docker + nginx 同源代理用的；Pages 上必须用控制台变量覆盖成后端绝对地址，否则请求会打到 Pages 自身并被 SPA 回退吞掉。
- 后端控制器映射本身不带 `/api` 前缀，变量值不要追加 `/api`，也不要带结尾斜杠。后端需公网 HTTPS，CORS 已由 `CorsConfig` 放行。
- `VITE_*` 在构建时写入产物，修改后需重新部署。

Vue Router 使用 history 模式；Pages 在没有 `404.html` 时会把未匹配路径回退到 `index.html`，无需 `_redirects`。

可选：Build watch paths 的 Include 填 `MioAIFrontend/*`，后端改动不触发前端重建。

## Docker

`Dockerfile` 为构建 + nginx 托管，`nginx.conf` 负责 SPA 回退、`/api` 反向代理与 SSE 透传。
