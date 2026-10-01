package com.mio.ai.customagent.service.mcp;

import com.mio.ai.customagent.model.entity.McpTool;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/9
 * @description: MCP客户端管理服务
 */
public interface McpClientManagerService {

    /**
     * 获取智能体关联的MCP工具列表
     * @param agentId 智能体ID
     * @return MCP工具列表
     */
    List<McpTool> getAgentMcpTools(Long agentId);

    /**
     * 初始化MCP客户端并获取工具回调
     * @param mcpTools MCP工具列表
     * @return 工具回调数组
     */
    ToolCallback[] initMcpToolCallbacks(List<McpTool> mcpTools);

    /**
     * 关闭所有MCP客户端
     */
    void closeAllClients();

    /**
     * 失效并关闭指定MCP工具的缓存客户端（更新配置/删除工具后调用，避免继续使用旧配置的连接）
     * @param mcpId MCP工具ID
     */
    void evictClient(Long mcpId);
}
