package com.mio.ai.resource.model.vo.agent;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/12
 * @description: 智能体发布版本视图（配置快照 + 绑定资源名称解析）
 */
@Data
public class AgentVersionVO implements Serializable {

    private Long id;

    private Long agentId;

    /**
     * 版本号（v1.0 格式）
     */
    private String version;

    private String name;

    private String description;

    private String avatar;

    private String icon;

    private String systemPrompt;

    /**
     * 回滚来源版本号（回滚产生的版本才有）
     */
    private String rollbackFrom;

    private Date createTime;

    /**
     * 版本发布时绑定的资源（名称已解析，targetDeleted=true 表示资源此后被删除，回滚时会跳过）
     */
    private List<VersionBinding> bindings;

    @Data
    public static class VersionBinding implements Serializable {

        /**
         * 资源类型：kb / mcp / skill
         */
        private String type;

        private Long targetId;

        private String name;

        private Boolean targetDeleted;
    }
}
