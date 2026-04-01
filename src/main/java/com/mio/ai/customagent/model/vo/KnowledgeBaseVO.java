package com.mio.ai.customagent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 知识库视图对象
 */
@Data
public class KnowledgeBaseVO implements Serializable {

    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

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

    /**
     * 状态
     */
    private Integer status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 文档数量
     */
    private Integer documentCount;

    /**
     * 总分块数
     */
    private Integer totalChunks;

    /**
     * 存储大小（字节）
     */
    private Long storageSize;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
