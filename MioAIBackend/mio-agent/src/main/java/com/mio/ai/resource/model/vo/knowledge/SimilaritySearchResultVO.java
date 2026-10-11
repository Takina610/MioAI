package com.mio.ai.resource.model.vo.knowledge;

import lombok.Data;

/**
 * @author: Takina
 * @date: 2026/4/6
 * @description: 相似度搜索结果VO
 */
@Data
public class SimilaritySearchResultVO {

    private String id;

    private String text;

    /**
     * 得分：向量命中=余弦相似度；仅关键词命中=命中率折算分；重排后=重排分数
     */
    private Double score;

    private String fileName;

    /**
     * 来源文档ID
     */
    private Long docId;

    /**
     * 分块序号（文档内）
     */
    private Integer chunkIndex;

    /**
     * 命中方式：vector=向量语义；keyword=关键词；rerank=经重排
     */
    private String matchType;
}
