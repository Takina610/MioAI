package com.mio.ai.resource.service.security;

import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.resource.model.entity.KnowledgeBase;
import com.mio.ai.resource.model.entity.McpTool;
import com.mio.ai.resource.service.knowledge.KnowledgeBaseService;
import com.mio.ai.resource.service.mcp.McpToolService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @author: Takina
 * @date: 2026/9/29
 * @description: 资源归属与可见性校验服务
 * <p>统一收敛"谁能读/改哪个资源"的判断逻辑：
 * <ul>
 *   <li>写操作：只允许资源所有者本人</li>
 *   <li>读操作：所有者本人，或公开（is_public = 1）的资源</li>
 * </ul>
 */
@Service
public class AccessGuardService {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private McpToolService mcpToolService;

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
