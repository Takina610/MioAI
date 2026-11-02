#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MioAI 本地 Embedding 服务（OpenAI 兼容 /v1/embeddings）

用途：当没有可用的外部 embedding API Key 时，在本地用真实语义模型提供嵌入能力，
使知识库的「上传 → 向量化 → 相似度检索」全链路可用（不依赖任何第三方配额）。

模型：intfloat/multilingual-e5-large（1024 维，支持中英文，与项目
      spring.ai.openai.embedding 的维度配置一致）。
依赖：pip install fastembed

启动：python MioAIBackend/embed/embed_server.py [--port 8099] [--model <name>]
后端配置（.env）：
    EMBED_APIKEY=local
    EMBED_BASE_URL=http://127.0.0.1:8099/v1
    EMBED_MODEL=multilingual-e5-large
"""

import argparse
import json
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DEFAULT_MODEL = "intfloat/multilingual-e5-large"
DEFAULT_PORT = 8099


def load_model(model_name: str):
    try:
        from fastembed import TextEmbedding
    except ImportError:
        print("[error] 未安装 fastembed，请先执行: pip install fastembed", file=sys.stderr)
        raise SystemExit(2)
    print(f"[embed] 正在加载模型 {model_name}（首次运行会自动下载，约 2GB）...", flush=True)
    model = TextEmbedding(model_name=model_name)
    print("[embed] 模型加载完成", flush=True)
    return model


def normalize(vec):
    """归一化：pgvector 用余弦距离，未归一化的向量会让相似度失真。"""
    norm = sum(x * x for x in vec) ** 0.5
    return [x / norm for x in vec] if norm else vec


def build_handler(model, model_alias: str):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def _send(self, status: int, payload: dict):
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            # 健康检查（部分客户端会先探活）
            if self.path.rstrip("/") in ("/health", "/v1/health", ""):
                self._send(200, {"status": "ok", "model": model_alias})
            else:
                self._send(404, {"error": {"message": "not found"}})

        def do_POST(self):
            if not self.path.rstrip("/").endswith("/embeddings"):
                self._send(404, {"error": {"message": "not found"}})
                return
            try:
                length = int(self.headers.get("Content-Length", 0))
                body = json.loads(self.rfile.read(length) or b"{}")
            except Exception as exc:
                self._send(400, {"error": {"message": f"invalid request body: {exc}"}})
                return

            raw_input = body.get("input")
            if raw_input is None:
                self._send(400, {"error": {"message": "input is required"}})
                return
            texts = raw_input if isinstance(raw_input, list) else [raw_input]
            if not texts or any(not isinstance(t, str) for t in texts):
                self._send(400, {"error": {"message": "input must be a string or string array"}})
                return

            try:
                # e5 系列训练时要求前缀：检索场景的查询用 query，文档用 passage。
                # 本服务不区分调用方语义（同一次请求可能混合），统一用 passage，
                # 对同一模型保持一致即可保证检索有效。
                prefixed = [f"passage: {t}" for t in texts]
                vectors = [normalize(list(map(float, v))) for v in model.embed(prefixed)]
            except Exception as exc:
                print(f"[embed] 计算失败: {exc}", file=sys.stderr, flush=True)
                self._send(500, {"error": {"message": f"embedding failed: {exc}"}})
                return

            self._send(200, {
                "object": "list",
                "data": [
                    {"object": "embedding", "index": i, "embedding": v}
                    for i, v in enumerate(vectors)
                ],
                "model": body.get("model") or model_alias,
                "usage": {"prompt_tokens": 0, "total_tokens": 0},
            })

        def log_message(self, fmt, *args):
            # 精简访问日志：只打印错误，避免向量化时刷屏
            if args and str(args[1]) != "200":
                print(f"[embed] {fmt % args}", file=sys.stderr, flush=True)

    return Handler


def main():
    parser = argparse.ArgumentParser(description="MioAI 本地 OpenAI 兼容 embedding 服务")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--model", default=DEFAULT_MODEL)
    args = parser.parse_args()

    model = load_model(args.model)
    alias = args.model.split("/")[-1]
    server = ThreadingHTTPServer((args.host, args.port), build_handler(model, alias))
    print(f"[embed] 监听 http://{args.host}:{args.port}/v1/embeddings  (model={alias}, dim=1024)", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("[embed] 停止", flush=True)
        server.shutdown()


if __name__ == "__main__":
    main()
