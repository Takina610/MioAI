package com.mio.ai.resource.service.mcp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.resource.model.dto.mcp.McpToolAddRequest;
import com.mio.ai.resource.model.dto.mcp.McpToolQueryRequest;
import com.mio.ai.resource.model.dto.mcp.McpToolUpdateRequest;
import com.mio.ai.resource.model.entity.mcp.McpTool;
import com.mio.ai.resource.model.vo.mcp.McpToolVO;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: MCP工具服务接口
 */
public interface McpToolService extends IService<McpTool> {

    /**
     * 创建MCP工具
     */
    Long addMcpTool(McpToolAddRequest request, Long userId);

    /**
     * 更新MCP工具
     */
    boolean updateMcpTool(McpToolUpdateRequest request, Long userId);

    /**
     * 删除MCP工具
     */
    boolean deleteMcpTool(Long id, Long userId);

    /**
     * 根据ID获取MCP工具
     */
    McpToolVO getMcpToolById(Long id);

    /**
     * 根据ID获取MCP工具（按查看者决定是否脱敏：所有者返回原始配置，其他人掩码凭证）
     */
    McpToolVO getMcpToolById(Long id, Long viewerUserId);

    /**
     * 根据ID获取MCP工具。
     *
     * @param includeSecrets 管理端审阅场景显式放开：无条件返回原始配置；业务侧按所有者判定
     */
    McpToolVO getMcpToolById(Long id, Long viewerUserId, boolean includeSecrets);

    /**
     * 分页查询MCP工具
     */
    Page<McpToolVO> queryMcpTools(McpToolQueryRequest request);

    /**
     * 获取公开的MCP工具列表（广场）
     */
    Page<McpToolVO> getPublicMcpTools(long current, long size);
}
