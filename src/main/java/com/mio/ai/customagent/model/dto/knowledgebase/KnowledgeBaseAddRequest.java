package com.mio.ai.customagent.model.dto.knowledgebase;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 知识库创建请求
 */
@Data
public class KnowledgeBaseAddRequest implements Serializable {

    /**
     * 知识库名称
     */
    private String name;

    /**
     * 知识库描述
     */
    private String description;

    /**
     * 分块大小
     */
    private Integer chunkSize;

    /**
     * 分块重叠大小
     */
    private Integer chunkOverlap;
}
