package com.mio.ai.resource.model.dto.agentknowledge;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/11
 * @description: 智能体-知识库绑定更新请求（检索配置 + 启用开关）
 */
@Data
public class AgentKnowledgeUpdateRequest implements Serializable {

    /**
     * 检索返回数量上限（1~50）
     */
    private Integer topK;

    /**
     * 相似度阈值（0~1）
     */
    private Double threshold;

    /**
     * 检索方式：vector / hybrid
     */
    private String method;

    /**
     * 是否启用重排
     */
    private Boolean enableRerank;

    /**
     * 重排后保留数量（1~20）
     */
    private Integer rerankTopN;

    /**
     * 绑定是否启用（0-禁用 1-启用）
     */
    private Integer enabled;
}
