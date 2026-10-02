package com.mio.ai.customagent.service.mcp.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.mio.ai.customagent.model.dto.mcptool.McpValidateRequest;
import com.mio.ai.customagent.model.vo.mcp.McpValidateResultVO;
import com.mio.ai.customagent.service.mcp.McpValidateService;
import com.mio.ai.framework.mcp.McpClientFactory;
import com.mio.ai.framework.mcp.McpHttpErrorProbe;
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
            McpValidateResultVO error = buildErrorResult(e, serverConfig);
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

    private static final int MESSAGE_MAX = 200;

    /**
     * 错误分类与还原（包级可见便于测试）：
     * HTTP 型配置用探针拿服务端真实状态码/响应体；STDIO 走 cause 链，把根因透出到错误消息里
     */
    McpValidateResultVO buildErrorResult(Exception e, JSONObject serverConfig) {
        McpValidateResultVO result = new McpValidateResultVO();
        result.setSuccess(false);

        String url = serverConfig.getStr("url");
        if (url != null && !url.isBlank()) {
            return buildHttpErrorResult(result, serverConfig, collectCauseMessages(e));
        }
        String messages = collectCauseMessages(e);
        return applyType(result, messages, "校验失败：" + messages);
    }

    private McpValidateResultVO buildHttpErrorResult(McpValidateResultVO result, JSONObject serverConfig,
                                                     String sdkMessage) {
        McpHttpErrorProbe.ProbeResult probe = McpHttpErrorProbe.probe(serverConfig);
        if (probe.networkFailed()) {
            return applyType(result, probe.networkError(), "连接失败：" + probe.networkError());
        }
        int status = probe.statusCode();
        if (status == 401 || status == 403) {
            result.setErrorType("AUTH_FAILED");
            result.setErrorMessage("认证失败：服务端返回 " + withBody(status, probe.bodyExcerpt())
                    + "，需在配置 headers 中提供令牌");
            return result;
        }
        if (status == 404 || status == 405) {
            // 405 常见于把 SSE 端点配成 type=http：SSE 端点不接受 POST
            result.setErrorType("CONNECTION_FAILED");
            String hint = status == 405 ? "，URL 可能不是 MCP 端点或需要 type=sse" : "，URL 可能不是 MCP 端点";
            result.setErrorMessage("连接失败：服务端返回 " + withBody(status, probe.bodyExcerpt()) + hint);
            return result;
        }
        if (status >= 400) {
            result.setErrorType("UNKNOWN");
            result.setErrorMessage("校验失败：服务端返回 " + withBody(status, probe.bodyExcerpt()));
            return result;
        }
        // 2xx/3xx：端点可达但 SDK 握手失败，多为协议版本不兼容或非 MCP 端点
        result.setErrorType("UNKNOWN");
        result.setErrorMessage("服务器可达但 MCP 握手失败（" + sdkMessage + "），可能协议版本不兼容");
        return result;
    }

    /** 按关键字定 errorType；消息自带类型前缀，前端不再二次拼接 */
    private McpValidateResultVO applyType(McpValidateResultVO result, String keywordText, String displayMessage) {
        String lower = keywordText.toLowerCase();
        String prefix;
        if (lower.contains("timeout") || lower.contains("timed out")) {
            result.setErrorType("TIMEOUT");
            prefix = "连接超时：";
        } else if (lower.contains("401") || lower.contains("403") || lower.contains("unauthorized")
                || lower.contains("forbidden") || lower.contains("api key") || lower.contains("apikey")
                || lower.contains("auth")) {
            result.setErrorType("AUTH_FAILED");
            prefix = "认证失败：";
        } else if (lower.contains("404") || lower.contains("refused") || lower.contains("unreachable")
                || lower.contains("enoent") || lower.contains("not found") || lower.contains("connection")
                || lower.contains("unknown host") || lower.contains("unknownhost")
                || lower.contains("cannot run program") || lower.contains("createprocess")) {
            result.setErrorType("CONNECTION_FAILED");
            prefix = "连接失败：";
        } else {
            result.setErrorType("UNKNOWN");
            prefix = "校验失败：";
        }
        result.setErrorMessage(prefix + displayMessage);
        return result;
    }

    private String withBody(int status, String bodyExcerpt) {
        return bodyExcerpt == null || bodyExcerpt.isEmpty()
                ? String.valueOf(status)
                : status + "（" + bodyExcerpt + "）";
    }

    /** 收集异常链上各级消息（SDK/ProcessBuilder 常把真实原因放在 cause 里），去重后拼接并截断 */
    private String collectCauseMessages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        while (cur != null) {
            String msg = cur.getMessage() != null && !cur.getMessage().isBlank()
                    ? cur.getMessage() : cur.getClass().getSimpleName();
            if (sb.indexOf(msg) < 0) {
                if (sb.length() > 0) {
                    sb.append(" | ");
                }
                sb.append(msg);
            }
            cur = cur.getCause() == cur ? null : cur.getCause();
        }
        return sb.length() > MESSAGE_MAX ? sb.substring(0, MESSAGE_MAX) + "..." : sb.toString();
    }
}
