package com.mio.ai.resource.service.mcp.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mio.ai.framework.mcp.McpClientFactory;
import com.mio.ai.resource.mapper.mcp.McpToolMapper;
import com.mio.ai.resource.model.entity.McpTool;
import com.mio.ai.resource.service.mcp.McpClientManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author: Takina
 * @date: 2026/4/9
 * @description: MCP客户端管理服务实现
 */
@Service
@Slf4j
public class McpClientManagerServiceImpl implements McpClientManagerService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @Autowired
    private McpToolMapper mcpToolMapper;

    @Autowired
    private McpClientFactory mcpClientFactory;

    private final ConcurrentHashMap<Long, McpClientFactory.McpClientHandle> clientCache = new ConcurrentHashMap<>();

    @Override
    public ToolCallback[] getPublicMcpToolCallbacks() {
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpTool::getIsPublic, 1)
                .eq(McpTool::getStatus, 1);
        List<McpTool> publicTools = mcpToolMapper.selectList(wrapper);
        if (publicTools.isEmpty()) {
            return new ToolCallback[0];
        }
        return initMcpToolCallbacks(publicTools);
    }

    @Override
    public ToolCallback[] initMcpToolCallbacks(List<McpTool> mcpTools) {
        if (mcpTools == null || mcpTools.isEmpty()) {
            return new ToolCallback[0];
        }

        List<ToolCallback> allCallbacks = new ArrayList<>();

        for (McpTool mcpTool : mcpTools) {
            try {
                // 复用缓存的客户端，避免每次对话都新建连接（旧实现每次 put 覆盖且不 close，造成进程/连接泄漏）
                McpClientFactory.McpClientHandle handle = clientCache.get(mcpTool.getId());
                if (handle == null) {
                    handle = createHandle(mcpTool);
                    if (handle == null) {
                        continue;
                    }
                    clientCache.put(mcpTool.getId(), handle);
                }
                List<ToolCallback> callbacks = McpToolUtils.getToolCallbacksFromSyncClients(List.of(handle.client()));
                allCallbacks.addAll(callbacks);
                log.info("成功初始化MCP工具: {} - {}", mcpTool.getName(), mcpTool.getId());
            } catch (Exception e) {
                // 缓存的客户端可能已失效（服务端重启等）：关闭并重建一次
                closeQuietly(clientCache.remove(mcpTool.getId()));
                try {
                    McpClientFactory.McpClientHandle recreated = createHandle(mcpTool);
                    if (recreated != null) {
                        clientCache.put(mcpTool.getId(), recreated);
                        List<ToolCallback> callbacks = McpToolUtils.getToolCallbacksFromSyncClients(List.of(recreated.client()));
                        allCallbacks.addAll(callbacks);
                        log.info("重建MCP客户端成功: {} - {}", mcpTool.getName(), mcpTool.getId());
                        continue;
                    }
                } catch (Exception retryError) {
                    closeQuietly(clientCache.remove(mcpTool.getId()));
                }
                log.error("初始化MCP工具失败: {} - {}", mcpTool.getName(), mcpTool.getId(), e);
            }
        }

        return allCallbacks.toArray(new ToolCallback[0]);
    }

    @Override
    public void evictClient(Long mcpId) {
        if (mcpId == null) {
            return;
        }
        closeQuietly(clientCache.remove(mcpId));
    }

    private void closeQuietly(McpClientFactory.McpClientHandle handle) {
        if (handle == null) {
            return;
        }
        try {
            handle.close();
        } catch (Exception e) {
            log.warn("关闭MCP客户端失败: {}", e.getMessage());
        }
    }

    /**
     * 解析工具配置并创建客户端；配置结构问题返回 null（记 warn），连接失败抛异常（由调用方决定重试）
     */
    private McpClientFactory.McpClientHandle createHandle(McpTool mcpTool) {
        String config = mcpTool.getConfig();
        if (config == null || config.isBlank()) {
            log.warn("MCP工具配置为空: {}", mcpTool.getName());
            return null;
        }

        JSONObject configJson;
        try {
            configJson = JSONUtil.parseObj(config);
        } catch (Exception e) {
            log.warn("MCP工具配置JSON无效: {} - {}", mcpTool.getName(), e.getMessage());
            return null;
        }
        JSONObject mcpServers = configJson.getJSONObject("mcpServers");
        if (mcpServers == null || mcpServers.isEmpty()) {
            log.warn("MCP配置中未找到mcpServers节点: {}", mcpTool.getName());
            return null;
        }
        String serverName = mcpServers.keySet().iterator().next();
        JSONObject serverConfig = mcpServers.getJSONObject(serverName);
        if (serverConfig == null) {
            log.warn("MCP服务器配置为空: {}", mcpTool.getName());
            return null;
        }

        return mcpClientFactory.createSyncClient("mio-ai-agent-" + mcpTool.getId(), serverConfig, REQUEST_TIMEOUT);
    }

    @Override
    public void closeAllClients() {
        for (Map.Entry<Long, McpClientFactory.McpClientHandle> entry : clientCache.entrySet()) {
            try {
                entry.getValue().close();
                log.info("关闭MCP客户端: {}", entry.getKey());
            } catch (Exception e) {
                log.error("关闭MCP客户端失败: {}", entry.getKey(), e);
            }
        }
        clientCache.clear();
    }
}
