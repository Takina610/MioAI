package com.mio.ai.resource.model.vo.agent;

import com.mio.ai.resource.model.vo.knowledge.KnowledgeBaseVO;
import com.mio.ai.resource.model.vo.mcp.McpToolVO;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/8
 * @description: 智能体详情视图对象
 */
@Data
public class AgentDetailVO implements Serializable {

    private Long id;

    private Long userId;

    private String name;

    private String description;

    private String avatar;

    private Integer type;

    private String typeDesc;

    private String systemPrompt;

    private Integer status;

    private String statusDesc;

    private Integer isPublic;

    private Integer usageCount;

    private String version;

    private Date createTime;

    private Date updateTime;

    private List<KnowledgeBaseVO> knowledgeBases;

    private List<McpToolVO> mcpTools;
}
