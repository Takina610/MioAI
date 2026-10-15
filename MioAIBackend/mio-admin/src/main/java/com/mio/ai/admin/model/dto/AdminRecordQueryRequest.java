package com.mio.ai.admin.model.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理后台记录级查询通用请求：各管理页按需传字段，其余为 null 即不过滤
 */
@Data
public class AdminRecordQueryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 页码，默认 1 */
    private long current = 1;

    /** 每页数量，默认 20 */
    private long pageSize = 20;

    /** 关键词：按各记录主体字段模糊匹配（标题/名称/路径/内容等） */
    private String keyword;

    /** 按归属用户名模糊过滤（会话/内容管理） */
    private String ownerName;

    /** 按智能体名称模糊过滤（会话管理） */
    private String agentName;

    /** 会话置顶过滤（0-否 1-是） */
    private Integer pinned;

    /** MCP 工具调用记录：按 MCP 工具 id 关联（后端解析 mcp__<server>__ 前缀） */
    private Long mcpId;

    /** 会话消息内容关键词（搜索 agent_message.blocks 后回溯会话） */
    private String messageKeyword;

    private Long userId;

    private Long agentId;

    private Long skillId;

    private Long kbId;

    private Long groupAgentId;

    private Long memberAgentId;

    private String conversationId;

    /** 状态/成功失败过滤：日志表为 1 成功 0 失败（tool 2 超时），任务表为 running 等字符串时放 taskStatus */
    private Integer status;

    /** 后台任务状态（running/completed/failed/stopped/interrupted） */
    private String taskStatus;

    /** activity 日志的业务模块（agent/knowledge/mcp/skill/model/chat/auth） */
    private String module;

    /** 后台任务类型（bash/agent） */
    private String type;

    /** usage 日志的模型名 */
    private String model;

    /** daily 统计起始日期（yyyy-MM-dd） */
    private String startDate;

    /** daily 统计截止日期（yyyy-MM-dd） */
    private String endDate;
}
