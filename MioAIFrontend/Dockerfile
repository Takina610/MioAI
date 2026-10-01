# MioAI 前端镜像（构建 + nginx 托管）
FROM node:20-alpine AS build
WORKDIR /app
# 固定 pnpm 版本（与本地/锁文件一致）；依赖构建脚本白名单见 pnpm-workspace.yaml 的 allowBuilds
RUN npm install -g pnpm@12.6.0
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
