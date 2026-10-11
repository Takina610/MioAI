package com.mio.ai.resource.model.vo.knowledge;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/11
 * @description: 文档向量分块视图（切片查看）
 */
@Data
public class DocumentChunkVO implements Serializable {

    /**
     * 向量库分块ID，如 doc_12_3
     */
    private String chunkId;

    /**
     * 分块序号（文档内从 0 开始）
     */
    private Integer chunkIndex;

    /**
     * 分块正文
     */
    private String content;

    /**
     * 分块字符长度
     */
    private Integer length;

    private String fileName;
}
