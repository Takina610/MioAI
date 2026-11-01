# MioAI 生产部署（Oracle Cloud / ARM64）

前端托管在 Cloudflare Pages（`ai.tak1na.cn`、`ai.kmik.tech`），本目录只部署后端与其依赖。

## 服务器约束（部署时必须遵守）

- **安全组只放行 22 / 80**：443 与其它端口在实例外部被拦截，服务器上开防火墙规则无效。
  因此后端统一由宿主 80 端口对外，HTTPS 由 Cloudflare 侧终结（回源走 HTTP）。
- **宿主网卡 MTU 9000，docker bridge 1500**：用 `ports:` 映射到公网 IP 会因路径 MTU
  不一致丢包（TCP 握手成功但 HTTP 超时）。对外服务必须用 `network_mode: host`。
- 公网 IP 是 Oracle NAT 地址，实机网卡为 `10.0.0.212`，不支持 hairpin，服务器上自测
  公网 IP 不通属正常，需从外部验证。

## 部署步骤

```bash
cd /opt/mioai
cp .env.example .env && vim .env          # 填真实密钥（见下）
docker compose build backend
docker compose up -d mysql postgres redis
# 基础表由 mysql 镜像的 initdb 自动导入，增量迁移需手动按序执行：
for f in MioAIBackend/sql/*.sql; do
  docker exec -i mio-ai-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" mio_ai < "$f"
done
docker compose up -d backend
docker compose -f deploy/nginx-proxy.yml up -d
```

## 必填环境变量

| 变量 | 说明 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` / `MYSQL_PASSWORD` | MySQL 密码 |
| `PG_PASSWORD` | pgvector 密码 |
| `MODEL_BASE_URL` | 对话模型网关，须为容器可达的公网地址 |
| `MODEL_CHAT_MODEL` / `MODEL_API_KEY` | 模型名与密钥 |
| `EMBED_APIKEY` / `EMBED_BASE_URL` | Embedding 网关（知识库向量化依赖） |

`docker-compose.yml` 已透传 `MODEL_*`；不传时后端会退回默认的 `127.0.0.1:8090`
（本机 opencode2api 反代地址），容器内不可达。

## 已知问题

- **SSE 经 nginx 正常**（`proxy_buffering off` + 600s 超时），已实测 `text/event-stream`。
- MySQL 基础表来自 `MioAIBackend/mio_ai.sql`，后续新增表（如 `user_activity_log`）
  在 `MioAIBackend/sql/` 的增量脚本里，漏跑会导致登录成功但日志落库报错。
- Oracle 安全组若日后放行 443，可直接在本目录加 TLS server 块或套 Cloudflare 回源证书，
  无需改动后端。
