package com.mio.ai.framework.rag;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @author: Takina
 * @date: 2026/10/11
 * @description: 直连 pgvector 表的查询客户端，承担两类向量检索覆盖不了的工作：
 *               1) 关键词召回（ILIKE 子串匹配，专有名词/精确术语），供混合检索做 RRF 融合；
 *               2) 按文档枚举全部分块（切片查看）。
 *               JdbcTemplate 延迟获取：向量库数据源在 VectorStoreConfig 的 vectorStore Bean 内创建，
 *               不能注册为 Spring Bean（否则 JdbcTemplate 自动配置退避，影响 MySQL 侧）。
 */
@Slf4j
public class KnowledgeChunkQueryClient {

    private final Supplier<JdbcTemplate> jdbcTemplateSupplier;

    public KnowledgeChunkQueryClient(Supplier<JdbcTemplate> jdbcTemplateSupplier) {
        this.jdbcTemplateSupplier = jdbcTemplateSupplier;
    }

    /**
     * 向量库中的一行分块（metadata 中解析出的业务字段 + 原始 content）
     */
    public record ChunkRow(String id, String content, Long kbId, Long docId,
                           Integer chunkIndex, String fileName) {
    }

    /**
     * 关键词命中行：matchedTerms 为该行 content 实际命中的关键词个数，供相关性打分
     */
    public record KeywordHit(ChunkRow row, int matchedTerms) {
    }

    /**
     * 关键词召回：kbId 过滤 + content ILIKE 任一关键词。
     * 只做存在性过滤，相关性（命中词数）在 Java 侧计算，避免 SQL 方言绑定
     */
    public List<KeywordHit> searchByKeywords(Collection<Long> kbIds, List<String> terms, int limit) {
        if (kbIds == null || kbIds.isEmpty() || terms == null || terms.isEmpty()) {
            return List.of();
        }
        JdbcTemplate jdbc = jdbcTemplateSupplier.get();
        List<String> kbIdStrings = kbIds.stream().map(String::valueOf).toList();
        String inPlaceholders = String.join(",", kbIdStrings.stream().map(x -> "?").toList());
        String likePlaceholders = String.join(" OR ", terms.stream().map(x -> "content ILIKE ?").toList());

        String sql = "SELECT id, content, metadata FROM vector_store"
                + " WHERE (metadata->>'kbId') IN (" + inPlaceholders + ")"
                + " AND (" + likePlaceholders + ")"
                + " LIMIT " + Math.max(1, limit);

        Object[] args = new Object[kbIdStrings.size() + terms.size()];
        int idx = 0;
        for (String kbId : kbIdStrings) {
            args[idx++] = kbId;
        }
        for (String term : terms) {
            args[idx++] = "%" + escapeLike(term) + "%";
        }

        List<KeywordHit> hits = new ArrayList<>();
        jdbc.query(sql, rs -> {
            ChunkRow row = toRow(rs.getString("id"), rs.getString("content"), rs.getObject("metadata"));
            int matched = countMatchedTerms(row.content(), terms);
            if (matched > 0) {
                hits.add(new KeywordHit(row, matched));
            }
        }, args);
        return hits;
    }

    /**
     * 枚举某文档的全部向量分块（按 chunkIndex 升序），用于切片查看
     */
    public List<ChunkRow> listByDocId(long docId) {
        JdbcTemplate jdbc = jdbcTemplateSupplier.get();
        List<ChunkRow> rows = new ArrayList<>();
        jdbc.query("""
                SELECT id, content, metadata FROM vector_store
                WHERE metadata->>'docId' = ?
                ORDER BY CAST(metadata->>'chunkIndex' AS int)
                """, rs -> {
            rows.add(toRow(rs.getString("id"), rs.getString("content"), rs.getObject("metadata")));
        }, String.valueOf(docId));
        return rows;
    }

    /**
     * 统计某文档的分块数（文档统计与展示用）
     */
    public long countByDocId(long docId) {
        JdbcTemplate jdbc = jdbcTemplateSupplier.get();
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM vector_store WHERE metadata->>'docId' = ?",
                Long.class, String.valueOf(docId));
        return count != null ? count : 0;
    }

    private ChunkRow toRow(String id, String content, Object metadataObj) {
        Map<String, Object> metadata = parseMetadata(metadataObj);
        return new ChunkRow(
                id,
                content,
                toLong(metadata.get("kbId")),
                toLong(metadata.get("docId")),
                toInt(metadata.get("chunkIndex")),
                metadata.get("fileName") != null ? String.valueOf(metadata.get("fileName")) : null);
    }

    private Map<String, Object> parseMetadata(Object metadataObj) {
        try {
            String json = metadataObj == null ? null
                    : (metadataObj instanceof String s ? s : metadataObj.toString());
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            JSONObject obj = JSONUtil.parseObj(json);
            return obj;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private int countMatchedTerms(String content, List<String> terms) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        String lower = content.toLowerCase();
        int matched = 0;
        for (String term : terms) {
            if (lower.contains(term.toLowerCase())) {
                matched++;
            }
        }
        return matched;
    }

    private String escapeLike(String term) {
        return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value != null ? Long.valueOf(String.valueOf(value)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.valueOf(String.valueOf(value)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
