package com.mio.ai.customagent.model.dto.log;

import com.mio.ai.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: RAG检索日志查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RagRetrievalLogQueryRequest extends PageRequest implements Serializable {

    /**
     * 智能体ID
     */
    private Long agentId;

    /**
     * 知识库ID
     */
    private Long kbId;

    /**
     * 查询内容（模糊查询）
     */
    private String query;
}
