package com.mio.ai.resource.model.vo.knowledge;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/10/11
 * @description: 智能体-知识库绑定视图（含解析后的检索配置，供绑定管理界面直接表单绑定）
 */
@Data
public class AgentKnowledgeBindingVO implements Serializable {

    private Long id;

    private Long agentId;

    private Long kbId;

    /**
     * 知识库名称（冗余展示用）
     */
    private String kbName;

    /**
     * 检索返回数量上限
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
     * 重排后保留数量
     */
    private Integer rerankTopN;

    /**
     * 绑定是否启用
     */
    private Integer enabled;

    private Date createTime;
}
