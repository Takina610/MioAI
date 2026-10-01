package com.mio.ai.customagent.service.mcp.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.customagent.mapper.agent.AgentMcpMapper;
import com.mio.ai.customagent.mapper.mcp.McpToolMapper;
import com.mio.ai.customagent.model.entity.AgentMcp;
import com.mio.ai.customagent.model.entity.McpTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mio.ai.customagent.service.mcp.McpClientManagerService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
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

    @Autowired
    private AgentMcpMapper agentMcpMapper;

    @Autowired
    private McpToolMapper mcpToolMapper;

    private final JacksonMcpJsonMapper mapper = new JacksonMcpJsonMapper(new ObjectMapper());
    
    private final ConcurrentHashMap<Long, McpSyncClient> clientCache = new ConcurrentHashMap<>();

    @Override
    public List<McpTool> getAgentMcpTools(Long agentId) {
        LambdaQueryWrapper<AgentMcp> amWrapper = new LambdaQueryWrapper<>();
        amWrapper.eq(AgentMcp::getAgentId, agentId)
                .eq(AgentMcp::getEnabled, 1);
        List<AgentMcp> amList = agentMcpMapper.selectList(amWrapper);

        if (amList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> mcpIds = amList.stream().map(AgentMcp::getMcpId).toList();
        LambdaQueryWrapper<McpTool> mcpWrapper = new LambdaQueryWrapper<>();
        mcpWrapper.in(McpTool::getId, mcpIds)
                .eq(McpTool::getStatus, 1);
        Map<Long, McpTool> toolById = mcpToolMapper.selectList(mcpWrapper).stream()
                .collect(java.util.stream.Collectors.toMap(McpTool::getId, t -> t));

        // agent_mcp.config_override 真正生效：把绑定上的覆盖配置合并进工具的原始配置
        List<McpTool> result = new ArrayList<>();
        for (AgentMcp binding : amList) {
            McpTool tool = toolById.get(binding.getMcpId());
            if (tool == null) {
                continue;
            }
            if (binding.getConfigOverride() != null && !binding.getConfigOverride().isBlank()) {
                tool.setConfig(mergeConfigOverride(tool.getConfig(), binding.getConfigOverride()));
            }
            result.add(tool);
        }
        return result;
    }

    /**
     * 将绑定上的覆盖配置合并进 MCP 工具的原始配置。
     * 按 mcpServers 下同名（或第一个）服务节点合并：env 按键覆盖、args/url/command 存在则整体替换。
     */
    private String mergeConfigOverride(String baseConfig, String overrideConfig) {
        try {
            JSONObject base = JSONUtil.parseObj(baseConfig);
            JSONObject override = JSONUtil.parseObj(overrideConfig);
            JSONObject baseServers = base.getJSONObject("mcpServers");
            JSONObject overrideServers = override.getJSONObject("mcpServers");
            if (baseServers == null || baseServers.isEmpty() || overrideServers == null || overrideServers.isEmpty()) {
                return baseConfig;
            }
            String serverName = baseServers.keySet().iterator().next();
            JSONObject overrideServer = overrideServers.containsKey(serverName)
                    ? overrideServers.getJSONObject(serverName)
                    : overrideServers.getJSONObject(overrideServers.keySet().iterator().next());
            if (overrideServer == null) {
                return baseConfig;
            }
            JSONObject baseServer = baseServers.getJSONObject(serverName);
            JSONObject overrideEnv = overrideServer.getJSONObject("env");
            if (overrideEnv != null) {
                JSONObject env = baseServer.getJSONObject("env");
                if (env == null) {
                    baseServer.set("env", overrideEnv);
                } else {
                    env.putAll(overrideEnv);
                }
            }
            for (String key : List.of("args", "url", "command")) {
                if (overrideServer.containsKey(key)) {
                    baseServer.set(key, overrideServer.get(key));
                }
            }
            return base.toString();
        } catch (Exception e) {
            log.warn("合并 configOverride 失败，使用原始配置: {}", e.getMessage());
            return baseConfig;
        }
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
                McpSyncClient client = clientCache.get(mcpTool.getId());
                if (client == null) {
                    McpSyncClient created = createMcpClient(mcpTool);
                    if (created == null) {
                        log.warn("MCP客户端创建失败: {} - {}", mcpTool.getName(), mcpTool.getId());
                        continue;
                    }
                    client = created;
                    clientCache.put(mcpTool.getId(), client);
                }
                List<ToolCallback> callbacks = McpToolUtils.getToolCallbacksFromSyncClients(List.of(client));
                allCallbacks.addAll(callbacks);
                log.info("成功初始化MCP工具: {} - {}", mcpTool.getName(), mcpTool.getId());
            } catch (Exception e) {
                // 缓存的客户端可能已失效（服务端重启等）：关闭并重建一次
                closeQuietly(clientCache.remove(mcpTool.getId()));
                try {
                    McpSyncClient recreated = createMcpClient(mcpTool);
                    if (recreated != null) {
                        clientCache.put(mcpTool.getId(), recreated);
                        List<ToolCallback> callbacks = McpToolUtils.getToolCallbacksFromSyncClients(List.of(recreated));
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

    private void closeQuietly(McpSyncClient client) {
        if (client == null) {
            return;
        }
        try {
            client.close();
        } catch (Exception e) {
            log.warn("关闭MCP客户端失败: {}", e.getMessage());
        }
    }

    private McpSyncClient createMcpClient(McpTool mcpTool) {
        if (mcpTool.getConfig() == null || mcpTool.getConfig().isEmpty()) {
            log.warn("MCP工具配置为空: {}", mcpTool.getName());
            return null;
        }

        JSONObject configJson = JSONUtil.parseObj(mcpTool.getConfig());
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

        String url = serverConfig.getStr("url");
        String command = serverConfig.getStr("command");

        try {
            if (url != null && !url.isEmpty()) {
                return createSseClient(url, mcpTool.getId());
            } else if (command != null && !command.isEmpty()) {
                return createStdioClient(serverConfig, mcpTool.getId());
            } else {
                log.warn("MCP配置必须包含url(SSE模式)或command(STDIO模式): {}", mcpTool.getName());
                return null;
            }
        } catch (Exception e) {
            log.error("创建MCP客户端失败: {}", mcpTool.getName(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统错误");
        }
    }

    private McpSyncClient createSseClient(String url, Long mcpId) {
        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(url).build();

        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("mio-ai-agent-" + mcpId, "1.0.0"))
                .capabilities(McpSchema.ClientCapabilities.builder()
                        .roots(true)
                        .sampling()
                        .build())
                .requestTimeout(Duration.ofSeconds(30))
                .build();

        client.initialize();
        log.info("SSE MCP客户端初始化成功: {}", url);
        return client;
    }

    private McpSyncClient createStdioClient(JSONObject serverConfig, Long mcpId) {
        String command = serverConfig.getStr("command");
        JSONArray argsArray = serverConfig.getJSONArray("args");
        JSONObject envObj = serverConfig.getJSONObject("env");

        List<String> args = argsArray != null ? argsArray.toList(String.class) : List.of();
        Map<String, String> env = envObj != null ? envObj.toBean(Map.class) : Map.of();

        StdioClientTransport transport = new StdioClientTransport(
                ServerParameters.builder(command)
                        .args(args)
                        .env(env)
                        .build(),
                mapper
        );

        McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("mio-ai-agent-" + mcpId, "1.0.0"))
                .capabilities(McpSchema.ClientCapabilities.builder()
                        .roots(true)
                        .sampling()
                        .build())
                .requestTimeout(Duration.ofSeconds(60))
                .build();

        client.initialize();
        log.info("STDIO MCP客户端初始化成功: {}", command);
        return client;
    }

    @Override
    public void closeAllClients() {
        for (Map.Entry<Long, McpSyncClient> entry : clientCache.entrySet()) {
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
