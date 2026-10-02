package com.mio.ai.customagent.service.security;

import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.customagent.model.entity.Agent;
import com.mio.ai.customagent.model.entity.KnowledgeBase;
import com.mio.ai.customagent.model.entity.McpTool;
import com.mio.ai.customagent.model.enums.AgentStatusEnum;
import com.mio.ai.customagent.service.agent.AgentService;
import com.mio.ai.customagent.service.knowledge.KnowledgeBaseService;
import com.mio.ai.customagent.service.mcp.McpToolService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author: Takina
 * @date: 2026/9/29
 * @description: 资源归属与可见性校验服务
 * <p>统一收敛"谁能读/改哪个资源"的判断逻辑：
 * <ul>
 *   <li>写操作：只允许资源所有者本人</li>
 *   <li>读操作：所有者本人，或公开（is_public = 1）且处于已发布状态的资源</li>
 *   <li>系统内置智能体（user_id 为空）对所有用户（含游客）可见</li>
 * </ul>
 */
@Service
public class AccessGuardService {

    @Resource
    private AgentService agentService;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private McpToolService mcpToolService;

    /**
     * 校验用户对智能体的写权限（编辑/删除/绑定关系变更）
     */
    public Agent checkAgentOwner(Long agentId, Long userId) {
        if (agentId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        Agent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "智能体不存在");
        }
        if (agent.getUserId() == null || !agent.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该智能体");
        }
        return agent;
    }

    /**
     * 校验用户能否使用该智能体对话（要求已登录；所有者、内置智能体、或公开且已发布）
     */
    public Agent checkAgentUsable(Long agentId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return checkAgentReadable(agentId, userId);
    }

    /**
     * 校验能否查看该智能体详情：所有者、内置、或公开且已发布；
     * 未登录（userId 为 null）按游客处理，仅可查看内置与公开已发布的智能体。
     * 供智能体详情读取类接口使用；对话链路用 checkAgentUsable（要求登录）。
     */
    public Agent checkAgentReadable(Long agentId, Long userId) {
        if (agentId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        Agent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "智能体不存在");
        }
        boolean owner = userId != null && agent.getUserId() != null && agent.getUserId().equals(userId);
        boolean builtin = agent.getUserId() == null;
        boolean publicPublished = Integer.valueOf(1).equals(agent.getIsPublic())
                && Integer.valueOf(AgentStatusEnum.PUBLISHED.getCode()).equals(agent.getStatus());
        if (!owner && !builtin && !publicPublished) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权使用该智能体");
        }
        return agent;
    }

    /**
     * 校验用户能否读取知识库（所有者，或公开知识库）
     */
    public KnowledgeBase checkKbReadable(Long kbId, Long userId) {
        KnowledgeBase kb = getKb(kbId);
        boolean owner = userId != null && kb.getUserId() != null && kb.getUserId().equals(userId);
        if (!owner && !Integer.valueOf(1).equals(kb.getIsPublic())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该知识库");
        }
        return kb;
    }

    /**
     * 校验用户对知识库的写权限
     */
    public KnowledgeBase checkKbOwner(Long kbId, Long userId) {
        KnowledgeBase kb = getKb(kbId);
        if (userId == null || kb.getUserId() == null || !kb.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该知识库");
        }
        return kb;
    }

    /**
     * 校验用户能否读取 MCP 工具配置（所有者，或公开工具）
     */
    public McpTool checkMcpReadable(Long mcpId, Long userId) {
        McpTool mcpTool = mcpToolService.getById(mcpId);
        if (mcpTool == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "MCP工具不存在");
        }
        boolean owner = userId != null && mcpTool.getUserId() != null && mcpTool.getUserId().equals(userId);
        if (!owner && !Integer.valueOf(1).equals(mcpTool.getIsPublic())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该MCP工具");
        }
        return mcpTool;
    }

    /**
     * 校验用户对 MCP 工具的写权限
     */
    public McpTool checkMcpOwner(Long mcpId, Long userId) {
        McpTool mcpTool = mcpToolService.getById(mcpId);
        if (mcpTool == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "MCP工具不存在");
        }
        if (userId == null || mcpTool.getUserId() == null || !mcpTool.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限操作该MCP工具");
        }
        return mcpTool;
    }

    private KnowledgeBase getKb(Long kbId) {
        if (kbId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "知识库ID不能为空");
        }
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库不存在");
        }
        return kb;
    }
}
