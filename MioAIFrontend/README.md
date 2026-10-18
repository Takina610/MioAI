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
| `PNPM_VERSION` | `12.6.0` |
| `VITE_API_BASE_URL` | 后端公网地址，如 `https://api.example.com` |

- 构建命令与产物目录都相对 Root directory，产物目录写 `dist`，不要写 `MioAIFrontend/dist`。
- `PNPM_VERSION` 必须显式指定：Pages 默认版本不读取 `pnpm-workspace.yaml` 的 `allowBuilds`，依赖的构建脚本会被跳过。
- 仓库内 `.env` 的 `VITE_API_BASE_URL=/api` 是给 Docker + nginx 同源代理用的；Pages 上必须用控制台变量覆盖成后端绝对地址，否则请求会打到 Pages 自身并被 SPA 回退吞掉。
- 后端控制器映射本身不带 `/api` 前缀，变量值不要追加 `/api`，也不要带结尾斜杠。后端需公网 HTTPS，CORS 已由 `CorsConfig` 放行。
- `VITE_*` 在构建时写入产物，修改后需重新部署。

Vue Router 使用 history 模式；Pages 在没有 `404.html` 时会把未匹配路径回退到 `index.html`，无需 `_redirects`。

可选：Build watch paths 的 Include 填 `MioAIFrontend/*`，后端改动不触发前端重建。

## Docker

`Dockerfile` 为构建 + nginx 托管，`nginx.conf` 负责 SPA 回退、`/api` 反向代理与 SSE 透传。
