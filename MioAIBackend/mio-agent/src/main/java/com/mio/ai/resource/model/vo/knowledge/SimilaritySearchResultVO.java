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

    private Double score;

    private String fileName;
}
