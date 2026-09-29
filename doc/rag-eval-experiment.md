# RAG 检索效果消融实验设计（论文实验章节素材）

> 配套代码：`src/test/java/com/mio/ai/rag/RagEvaluationTest.java`
> 评测集：`src/test/resources/rag-eval/eval-dataset.json`（30 条问答用例）

## 1. 实验目的

验证 MioAI RAG 流水线中各组件对检索质量的贡献，回答三个问题：

1. **分块大小**（chunk size）如何影响检索命中率？
2. **关键词增强**（KeywordEnricher，LLM 抽取关键词写入元数据）是否提升召回？
3. **重排模型**（DashScope gte-rerank）在向量检索基础上能带来多少增益？

## 2. 实验环境与材料

| 项 | 值 |
|----|----|
| 知识库 | 内置 CS 比赛数据文档 4 篇（地图打法 / 赛事体系 / 选手&战队 / 道具战术，约 2 万字） |
| 嵌入模型 | DashScope text-embedding-v3（1024 维） |
| 向量库 | Redis Stack（RediSearch，HNSW） |
| 重排模型 | DashScope gte-rerank |
| 评测集 | 30 条中文问答对，覆盖赛事/选手/道具/地图四类，标注命中关键词与期望来源文档 |
| 指标 | Recall@k（top-k 至少一条命中关键词）、docHit@k（top-k 命中期望文档）、MRR（首命中排名倒数均值）、平均检索耗时 |

## 3. 实验变量（消融矩阵）

| 维度 | 取值 | 控制方式 |
|------|------|----------|
| 分块大小 | 400 / 800 / 1200 token | 配置 `mio.ai.rag.chunk-size`，每组需清空 Redis 重新向量化 |
| 关键词增强 | 开 / 关 | `KnowledgeBaseCreateServiceImpl.vectorizeFiles` 中注释 `keywordEnricher.enrich(...)` |
| 重排 | 开 / 关 | 配置 `mio.ai.rag.rerank-enabled` |
| topK | 3 / 5 / 10 | 评测脚本内置变体 |

> 单变量原则：每轮只改一个维度，其余保持默认（chunk=800、增强开、rerank 关）。

## 4. 运行步骤

```bash
# 1. 启动 MySQL / Redis Stack，配置好 application.yml（密钥等）

# 2. 创建一个知识库，上传 rag/ 目录下 4 篇文档并完成向量化，记下 kbId（假设为 1）

# 3. 运行评测（默认配置，向量检索基线）
RAG_EVAL=true RAG_EVAL_KB_ID=1 \
  mvn test -Dtest=RagEvaluationTest \
           -Dspring-boot.run.arguments="--mio.ai.rag.rerank-enabled=true"

# 4. 分块消融：改 -Dmio.ai.rag.chunk-size=400（或 1200），
#    清空向量库 → 重新向量化 → 重跑步骤 3

# 5. 结果输出在 target/rag-eval-results.md，直接可贴进论文
```

## 5. 结果记录模板

| 变体 | Recall@5 | docHit@5 | MRR | 平均耗时(ms) |
|------|----------|----------|-----|--------------|
| chunk=400 + 增强 + 无rerank | _待填_ | _待填_ | _待填_ | _待填_ |
| chunk=800 + 增强 + 无rerank（基线） | _待填_ | _待填_ | _待填_ | _待填_ |
| chunk=1200 + 增强 + 无rerank | _待填_ | _待填_ | _待填_ | _待填_ |
| chunk=800 + 无增强 + 无rerank | _待填_ | _待填_ | _待填_ | _待填_ |
| chunk=800 + 增强 + rerank | _待填_ | _待填_ | _待填_ | _待填_ |

## 6. 结果分析方法（论文写作要点）

- **分块大小**：预期 400 块小→语义破碎、召回率下降但定位更准（docHit 高、MRR 低）；1200 块大→单块包含多主题，召回稳定但答案定位噪声多。取最优值并说明"为什么 800 是折中"。
- **关键词增强**：对比 Recall@k 与 MRR 的变化；同时报告向量化耗时/成本的增加（每块多一次 LLM 调用），讨论性价比。
- **重排**：rerank 只改变排序不改变候选集，因此 Recall@10 不变、MRR 应提升；报告重排带来的额外时延（对比平均耗时列）。
- 结合 `rag_retrieval_log` 表的真实生产数据（检索耗时分布、命中分布）作为"系统运行分析"章节的补充证据。
