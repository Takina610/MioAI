package com.mio.ai.customagent.model.dto.agentknowledge;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-知识库关联创建请求
 */
@Data
public class AgentKnowledgeAddRequest implements Serializable {

    /**
     * 智能体ID
     */
    private Long agentId;

    /**
     * 知识库ID
     */
    private Long kbId;

    /**
     * 检索配置（JSON格式）
     */
    private String retrievalConfig;

    /**
     * 是否启用
     */
    private Integer enabled;
}
