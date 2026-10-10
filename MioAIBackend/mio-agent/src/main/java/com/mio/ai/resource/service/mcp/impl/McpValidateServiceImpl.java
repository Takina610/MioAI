package com.mio.ai.resource.service.mcp.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.mio.ai.resource.model.dto.mcptool.McpValidateRequest;
import com.mio.ai.resource.model.vo.mcp.McpValidateResultVO;
import com.mio.ai.resource.service.mcp.McpStdioGuard;
import com.mio.ai.resource.service.mcp.McpValidateService;
import com.mio.ai.framework.mcp.McpClientFactory;
import com.mio.ai.framework.mcp.McpHttpErrorProbe;
import com.mio.ai.framework.mcp.McpProcessStartException;
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

    /** 握手预算：SDK 默认 20s 且对启动失败/连接拒绝照等不误，校验场景缩短到 10s 让失败尽快暴露 */
    private static final Duration VALIDATE_INIT_TIMEOUT = Duration.ofSeconds(10);

    private final McpClientFactory mcpClientFactory;

    /** 可空：单测直接 new 时无门禁（仅跳过 STDIO 拦截，不影响错误分类逻辑） */
    private final McpStdioGuard stdioGuard;

    public McpValidateServiceImpl(McpClientFactory mcpClientFactory) {
        this(mcpClientFactory, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public McpValidateServiceImpl(McpClientFactory mcpClientFactory, McpStdioGuard stdioGuard) {
        this.mcpClientFactory = mcpClientFactory;
        this.stdioGuard = stdioGuard;
    }

    @Override
    public McpValidateResultVO validateMcpConfig(McpValidateRequest request, Long userId) {
        McpValidateResultVO result = new McpValidateResultVO();

        if (StringUtils.isBlank(request.getConfig())) {
            result.setSuccess(false);
            result.setErrorType("CONFIG_INVALID");
            result.setErrorMessage("配置不能为空");
            return result;
        }

        // 结构校验复用工厂实现：缺 mcpServers、缺 url/command 等在发起连接前就以 CONFIG_INVALID 拒绝
        String structureError = mcpClientFactory.validateStructure(request.getConfig());
        if (structureError != null) {
            result.setSuccess(false);
            result.setErrorType("CONFIG_INVALID");
            result.setErrorMessage(structureError);
            return result;
        }

        // STDIO 门禁：会在服务器上执行本地命令，非管理员默认拒绝
        if (stdioGuard != null) {
            String denyReason = stdioGuard.denyReason(request.getConfig(), userId);
            if (denyReason != null) {
                result.setSuccess(false);
                result.setErrorType("FORBIDDEN");
                result.setErrorMessage(denyReason);
                return result;
            }
        }

        JSONObject configJson = JSONUtil.parseObj(request.getConfig());
        JSONObject mcpServers = configJson.getJSONObject("mcpServers");

        List<String> warnings = new ArrayList<>();
        if (mcpServers.size() > 1) {
            warnings.add("配置包含 " + mcpServers.size() + " 个服务器节点，仅校验并使用第一个：" + mcpServers.keySet().iterator().next());
        }

        String serverName = mcpServers.keySet().iterator().next();
        JSONObject serverConfig = mcpServers.getJSONObject(serverName);

        try {
            try (McpClientFactory.McpClientHandle handle =
                         mcpClientFactory.createSyncClient("mio-ai-validator", serverConfig, VALIDATE_TIMEOUT,
                                 VALIDATE_INIT_TIMEOUT)) {
                McpSchema.ListToolsResult toolsResult = handle.client().listTools();
                result.setSuccess(true);
                result.setTools(extractToolInfos(toolsResult.tools()));
                result.setServerInfo(extractServerInfo(handle.initResult()));
                result.setWarnings(warnings);
                log.info("MCP校验成功: {} -> {} 个工具", serverName, toolsResult.tools().size());
            }
        } catch (McpProcessStartException e) {
            log.warn("MCP校验失败（命令不可启动）: {} - {}", serverName, e.getMessage());
            result.setSuccess(false);
            result.setErrorType("PROCESS_START_FAILED");
            result.setErrorMessage(e.getMessage());
            result.setWarnings(warnings);
            return result;
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
        // 展示消息即原因链原文，类型前缀由 applyType 统一添加（勿在此重复拼接）
        return applyType(result, collectCauseMessages(e), collectCauseMessages(e));
    }

    private McpValidateResultVO buildHttpErrorResult(McpValidateResultVO result, JSONObject serverConfig,
                                                     String sdkMessage) {
        McpHttpErrorProbe.ProbeResult probe = McpHttpErrorProbe.probe(serverConfig);
        if (probe.networkFailed()) {
            // 前缀由 applyType 统一加，这里只传原因原文，避免"连接失败：连接失败："
            return applyType(result, probe.networkError(), probe.networkError());
        }
        int status = probe.statusCode();
        if (status == 401 || status == 403) {
            result.setErrorType("AUTH_FAILED");
            result.setErrorMessage("认证失败：服务端返回 " + withBody(status, probe.bodyExcerpt())
                    + "，需在配置 headers 中提供令牌");
            return result;
        }
        if (status == 404 || status == 405) {
            // 405 常见于把 SSE 端点配成 type=http：SSE 端点不接受 POST；
            // 但用户已声明 type=sse 时同一提示就自相矛盾了，按已声明类型给对应解释
            result.setErrorType("CONNECTION_FAILED");
            String declaredType = serverConfig.getStr("type");
            boolean declaredSse = "sse".equalsIgnoreCase(declaredType == null ? "" : declaredType.trim());
            String hint;
            if (status == 405) {
                hint = declaredSse
                        ? "，该端点不接受 MCP 请求，可能已停止提供 SSE 服务"
                        : "，URL 可能不是 MCP 端点或需要 type=sse";
            } else {
                hint = "，URL 可能不是 MCP 端点";
            }
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
        if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("did not observe")) {
            result.setErrorType("TIMEOUT");
            prefix = "连接超时：";
        } else if (lower.contains("401") || lower.contains("403") || lower.contains("unauthorized")
                || lower.contains("forbidden") || lower.contains("api key") || lower.contains("apikey")
                || lower.contains("auth")) {
            result.setErrorType("AUTH_FAILED");
            prefix = "认证失败：";
        } else if (lower.contains("404") || lower.contains("refused") || lower.contains("unreachable")
                || lower.contains("enoent") || lower.contains("not found") || lower.contains("connection")
                || lower.contains("closedchannel") || lower.contains("broken pipe") || lower.contains("reset")
                || lower.contains("unknown host") || lower.contains("unknownhost")
                || lower.contains("cannot run program") || lower.contains("createprocess")
                || lower.contains("拒绝") || lower.contains("无法解析")) {
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
