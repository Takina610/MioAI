package com.mio.ai.resource.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: RAG检索日志实体类
 */
@Data
@TableName("rag_retrieval_log")
public class RagRetrievalLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 智能体ID
     */
    private Long agentId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 知识库ID
     */
    private Long kbId;

    /** 会话ID（群聊检索为空） */
    private String conversationId;

    /** 本轮对话模型（未指定为空） */
    private String model;

    /**
     * 查询内容
     */
    private String query;

    /**
     * 检索到的分块（JSON格式）
     */
    private String retrievedChunks;

    /**
     * 返回Top K数量
     */
    private Integer topK;

    /**
     * 相似度阈值
     */
    private Float scoreThreshold;

    /**
     * 响应时间（毫秒）
     */
    private Integer responseTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
