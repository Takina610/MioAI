package com.mio.ai.customagent.model.vo.log;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: RAG检索日志视图对象
 */
@Data
public class RagRetrievalLogVO implements Serializable {

    private Long id;

    /**
     * 智能体ID
     */
    private Long agentId;

    /**
     * 知识库ID
     */
    private Long kbId;

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
    private Date createTime;
}
