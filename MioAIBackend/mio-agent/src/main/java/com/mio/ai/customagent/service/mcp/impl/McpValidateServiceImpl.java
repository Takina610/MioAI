package com.mio.ai.customagent.service.mcp.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.mio.ai.customagent.model.dto.mcptool.McpValidateRequest;
import com.mio.ai.customagent.model.vo.mcp.McpValidateResultVO;
import com.mio.ai.customagent.service.mcp.McpValidateService;
import com.mio.ai.framework.mcp.McpClientFactory;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/7 14:30
 * @description:
 */

@Service
@Slf4j
public class McpValidateServiceImpl implements McpValidateService {

    private static final Duration VALIDATE_TIMEOUT = Duration.ofSeconds(30);

    private final McpClientFactory mcpClientFactory;

    public McpValidateServiceImpl(McpClientFactory mcpClientFactory) {
        this.mcpClientFactory = mcpClientFactory;
    }

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

        List<String> warnings = new ArrayList<>();
        if (mcpServers.size() > 1) {
            warnings.add("配置包含 " + mcpServers.size() + " 个服务器节点，仅校验并使用第一个：" + mcpServers.keySet().iterator().next());
        }

        String serverName = mcpServers.keySet().iterator().next();
        JSONObject serverConfig;
        try {
            serverConfig = mcpServers.getJSONObject(serverName);
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorType("CONFIG_INVALID");
            result.setErrorMessage("服务器节点必须是JSON对象");
            return result;
        }

        if (serverConfig == null) {
            result.setSuccess(false);
            result.setErrorType("CONFIG_INVALID");
            result.setErrorMessage("服务器配置为空");
            return result;
        }

        try {
            try (McpClientFactory.McpClientHandle handle =
                         mcpClientFactory.createSyncClient("mio-ai-validator", serverConfig, VALIDATE_TIMEOUT)) {
                McpSchema.ListToolsResult toolsResult = handle.client().listTools();
                result.setSuccess(true);
                result.setTools(extractToolInfos(toolsResult.tools()));
                result.setServerInfo(extractServerInfo(handle.initResult()));
                result.setWarnings(warnings);
                log.info("MCP校验成功: {} -> {} 个工具", serverName, toolsResult.tools().size());
            }
        } catch (Exception e) {
            log.error("MCP校验失败: {}", serverName, e);
            McpValidateResultVO error = buildErrorResult(e);
            error.setWarnings(warnings);
            return error;
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

        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        String lower = errorMsg.toLowerCase();
        if (lower.contains("timeout") || lower.contains("timed out")) {
            result.setErrorType("TIMEOUT");
            result.setErrorMessage("连接超时，请检查网络或服务器状态");
        } else if (lower.contains("401") || lower.contains("403") || lower.contains("unauthorized")
                || lower.contains("forbidden") || lower.contains("api key") || lower.contains("apikey")
                || lower.contains("auth")) {
            result.setErrorType("AUTH_FAILED");
            result.setErrorMessage("认证失败，请检查 API Key 或 headers 配置");
        } else if (lower.contains("404") || lower.contains("refused") || lower.contains("unreachable")
                || lower.contains("enoent") || lower.contains("not found") || lower.contains("connection")
                || lower.contains("unknown host") || lower.contains("unknownhost")) {
            result.setErrorType("CONNECTION_FAILED");
            result.setErrorMessage("连接失败，请检查服务器地址、命令路径或网络连接");
        } else {
            result.setErrorType("UNKNOWN");
            result.setErrorMessage("校验失败: " + errorMsg);
        }
        return result;
    }
}
