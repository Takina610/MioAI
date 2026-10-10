package com.mio.ai.resource.service.mcp;

import com.mio.ai.resource.model.entity.McpTool;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/9
 * @description: MCP客户端管理服务
 */
public interface McpClientManagerService {

    /**
     * 获取智能体绑定的 MCP 工具列表（enabled=1 且工具 status=1，合并 agent_mcp.config_override）
     * @param agentId 智能体ID
     * @return MCP工具列表
     */
    List<McpTool> getAgentMcpTools(Long agentId);

    /**
     * 初始化全部公共 MCP 工具的客户端并返回工具回调
     * <p>MioBot 是全站共享智能体，工具池 = 所有公开（is_public=1）且启用（status=1）的 MCP 工具
     *
     * @return 工具回调数组；单个工具初始化失败时跳过（日志记录），不影响其余工具
     */
    ToolCallback[] getPublicMcpToolCallbacks();

    /**
     * 同 {@link #getPublicMcpToolCallbacks()}，但带回逐工具的失败原因（供会话内提示用户）
     */
    McpInitResult getPublicMcpToolsDetailed();

    /**
     * 初始化指定 MCP 工具的客户端并返回工具回调
     *
     * @param mcpTools MCP工具列表
     * @return 工具回调数组
     */
    ToolCallback[] initMcpToolCallbacks(List<McpTool> mcpTools);

    /**
     * 同 {@link #initMcpToolCallbacks(List)}，但带回逐工具的失败原因（供会话内提示用户）
     */
    McpInitResult initMcpToolsDetailed(List<McpTool> mcpTools);

    /**
     * MCP 装配结果：可用回调 + 失败摘要（"工具名：原因"）
     */
    record McpInitResult(ToolCallback[] callbacks, List<String> failures) {
        public static McpInitResult empty() {
            return new McpInitResult(new ToolCallback[0], List.of());
        }
    }

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
