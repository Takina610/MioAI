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
        
        return mcpToolMapper.selectList(mcpWrapper);
    }

    @Override
    public ToolCallback[] initMcpToolCallbacks(List<McpTool> mcpTools) {
        if (mcpTools == null || mcpTools.isEmpty()) {
            return new ToolCallback[0];
        }

        List<ToolCallback> allCallbacks = new ArrayList<>();

        for (McpTool mcpTool : mcpTools) {
            try {
                McpSyncClient client = createMcpClient(mcpTool);
                if (client != null) {
                    clientCache.put(mcpTool.getId(), client);
                    
                    List<ToolCallback> callbacks = McpToolUtils.getToolCallbacksFromSyncClients(List.of(client));
                    allCallbacks.addAll(callbacks);
                    log.info("成功初始化MCP工具: {} - {}", mcpTool.getName(), mcpTool.getId());
                }
            } catch (Exception e) {
                log.error("初始化MCP工具失败: {} - {}", mcpTool.getName(), mcpTool.getId(), e);
            }
        }

        return allCallbacks.toArray(new ToolCallback[0]);
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
