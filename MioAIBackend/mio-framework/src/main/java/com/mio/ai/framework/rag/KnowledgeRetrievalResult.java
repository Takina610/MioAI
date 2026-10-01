package com.mio.ai.framework.rag;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * @author: Takina
 * @date: 2026/9/29
 * @description: 知识库检索命中的单个分块结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRetrievalResult implements Serializable {

    /**
     * 向量库中的分块ID，如 doc_12_3
     */
    private String chunkId;

    /**
     * 分块正文
     */
    private String text;

    /**
     * 相似度得分
     */
    private Double score;

    /**
     * 来源知识库ID
     */
    private Long kbId;

    /**
     * 来源文档数据库ID
     */
    private Long docId;

    /**
     * 来源文档文件名
     */
    private String fileName;

    /**
     * 原始元数据
     */
    private Map<String, Object> metadata;
}
