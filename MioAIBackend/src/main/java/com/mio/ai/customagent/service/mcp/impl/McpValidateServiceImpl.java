package com.mio.ai.customagent.service.mcp.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mio.ai.customagent.model.dto.mcptool.McpValidateRequest;
import com.mio.ai.customagent.model.vo.mcp.McpValidateResultVO;
import com.mio.ai.customagent.service.mcp.McpValidateService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author: Takina
 * @date: 2026/4/7 14:30
 * @description:
 */

@Service
@Slf4j
public class McpValidateServiceImpl implements McpValidateService {

    private final McpJsonMapper mapper = new JacksonMcpJsonMapper(new ObjectMapper());

    @Override
    public McpValidateResultVO validateMcpConfig(McpValidateRequest request) {
        McpValidateResultVO result = new McpValidateResultVO();

        if (StringUtils.isBlank(request.getConfig())) {
            result.setSuccess(false);
            result.setErrorType("CONFIG_INVALID");
            result.setErrorMessage("配置不能为空");
            return result;
        }

        JSONObject configJson;
        try {
            configJson = JSONUtil.parseObj(request.getConfig());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorType("CONFIG_INVALID");
            result.setErrorMessage("配置JSON格式无效: " + e.getMessage());
            return result;
        }

        JSONObject mcpServers = configJson.getJSONObject("mcpServers");
        if (mcpServers == null || mcpServers.isEmpty()) {
            result.setSuccess(false);
            result.setErrorType("CONFIG_INVALID");
            result.setErrorMessage("配置中未找到mcpServers节点");
            return result;
        }

        String serverName = mcpServers.keySet().iterator().next();
        JSONObject serverConfig = mcpServers.getJSONObject(serverName);

        if (serverConfig == null) {
            result.setSuccess(false);
            result.setErrorType("CONFIG_INVALID");
            result.setErrorMessage("服务器配置为空");
            return result;
        }

        try {
            String url = serverConfig.getStr("url");
            String command = serverConfig.getStr("command");

            if (StrUtil.isNotBlank(url)) {
                return validateSseClient(url);
            } else if (StrUtil.isNotBlank(command)) {
                return validateStdioClient(serverConfig);
            } else {
                result.setSuccess(false);
                result.setErrorType("CONFIG_INVALID");
                result.setErrorMessage("配置必须包含url(SSE模式)或command(STDIO模式)");
                return result;
            }
        } catch (Exception e) {
            log.error("MCP校验失败", e);
            return buildErrorResult(e);
        }
    }

    private McpValidateResultVO validateSseClient(String url) {
        McpValidateResultVO result = new McpValidateResultVO();
        try {
            HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(url).build();

            try (McpSyncClient client = McpClient.sync(transport)
                    .clientInfo(new McpSchema.Implementation("mio-ai-validator", "1.0.0"))
                    .capabilities(McpSchema.ClientCapabilities.builder()
                            .roots(true)
                            .sampling()
                            .build())
                    .requestTimeout(Duration.ofSeconds(30))
                    .build()) {

                McpSchema.InitializeResult initResult = client.initialize();
                log.info("SSE客户端初始化成功: {}", initResult);

                McpSchema.ListToolsResult toolsResult = client.listTools();
                List<McpValidateResultVO.McpToolInfo> toolInfos = extractToolInfos(toolsResult.tools());

                result.setSuccess(true);
                result.setTools(toolInfos);
                result.setServerInfo(extractServerInfo(initResult));
            }
        } catch (Exception e) {
            log.error("SSE客户端校验失败", e);
            return buildErrorResult(e);
        }
        return result;
    }

    private McpValidateResultVO validateStdioClient(JSONObject serverConfig) {
        McpValidateResultVO result = new McpValidateResultVO();

        String command = serverConfig.getStr("command");
        JSONArray argsArray = serverConfig.getJSONArray("args");
        JSONObject envObj = serverConfig.getJSONObject("env");

        List<String> args = argsArray != null ? argsArray.toList(String.class) : List.of();
        Map<String, String> env = envObj != null ? envObj.toBean(Map.class) : Map.of();

        try {
            StdioClientTransport transport = new StdioClientTransport(
                    ServerParameters.builder(command)
                            .args(args)
                            .env(env)
                            .build(),
                    mapper
            );

            try (McpSyncClient client = McpClient.sync(transport)
                    .clientInfo(new McpSchema.Implementation("mio-ai-validator", "1.0.0"))
                    .capabilities(McpSchema.ClientCapabilities.builder()
                            .roots(true)
                            .sampling()
                            .build())
                    .requestTimeout(Duration.ofSeconds(60))
                    .build()) {

                McpSchema.InitializeResult initResult = client.initialize();
                log.info("STDIO客户端初始化成功: {}", initResult);

                McpSchema.ListToolsResult toolsResult = client.listTools();
                List<McpValidateResultVO.McpToolInfo> toolInfos = extractToolInfos(toolsResult.tools());

                result.setSuccess(true);
                result.setTools(toolInfos);
                result.setServerInfo(extractServerInfo(initResult));
            }
        } catch (Exception e) {
            log.error("STDIO客户端校验失败", e);
            return buildErrorResult(e);
        }
        return result;
    }

    private List<McpValidateResultVO.McpToolInfo> extractToolInfos(List<McpSchema.Tool> tools) {
        List<McpValidateResultVO.McpToolInfo> toolInfos = new ArrayList<>();
        for (McpSchema.Tool tool : tools) {
            McpValidateResultVO.McpToolInfo toolInfo = new McpValidateResultVO.McpToolInfo();
            toolInfo.setName(tool.name());
            toolInfo.setDescription(tool.description());
            if (tool.inputSchema() != null) {
                toolInfo.setInputSchema(JSONUtil.toJsonStr(tool.inputSchema()));
            }
            toolInfos.add(toolInfo);
        }
        return toolInfos;
    }

    private McpValidateResultVO.ServerInfo extractServerInfo(McpSchema.InitializeResult initResult) {
        McpValidateResultVO.ServerInfo serverInfo = new McpValidateResultVO.ServerInfo();
        if (initResult.serverInfo() != null) {
            serverInfo.setName(initResult.serverInfo().name());
            serverInfo.setVersion(initResult.serverInfo().version());
        }
        serverInfo.setProtocolVersion(initResult.protocolVersion());
        return serverInfo;
    }

    private McpValidateResultVO buildErrorResult(Exception e) {
        McpValidateResultVO result = new McpValidateResultVO();
        result.setSuccess(false);

        String errorMsg = e.getMessage();
        if (errorMsg != null) {
            if (errorMsg.contains("timeout") || errorMsg.contains("Timeout")) {
                result.setErrorType("TIMEOUT");
                result.setErrorMessage("连接超时，请检查网络或服务器状态");
            } else if (errorMsg.contains("auth") || errorMsg.contains("Auth") ||
                    errorMsg.contains("key") || errorMsg.contains("API") ||
                    errorMsg.contains("401") || errorMsg.contains("403")) {
                result.setErrorType("AUTH_FAILED");
                result.setErrorMessage("认证失败，请检查API Key或认证信息");
            } else if (errorMsg.contains("connection") || errorMsg.contains("Connection") ||
                    errorMsg.contains("refused") || errorMsg.contains("unreachable") ||
                    errorMsg.contains("ENOENT") || errorMsg.contains("not found")) {
                result.setErrorType("CONNECTION_FAILED");
                result.setErrorMessage("连接失败，请检查服务器地址、命令路径或网络连接");
            } else {
                result.setErrorType("UNKNOWN");
                result.setErrorMessage("校验失败: " + errorMsg);
            }
        } else {
            result.setErrorType("UNKNOWN");
            result.setErrorMessage("校验失败: 未知错误");
        }
        return result;
    }
}
