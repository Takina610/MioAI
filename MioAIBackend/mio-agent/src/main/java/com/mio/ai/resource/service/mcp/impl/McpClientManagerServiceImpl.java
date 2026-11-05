package com.mio.ai.resource.service.mcp.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mio.ai.framework.mcp.McpClientFactory;
import com.mio.ai.resource.mapper.agent.AgentMcpMapper;
import com.mio.ai.resource.mapper.mcp.McpToolMapper;
import com.mio.ai.resource.model.entity.agent.AgentMcp;
import com.mio.ai.resource.model.entity.mcp.McpTool;
import com.mio.ai.resource.service.mcp.McpClientManagerService;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private AgentMcpMapper agentMcpMapper;

    @Autowired
    private McpToolMapper mcpToolMapper;

    @Autowired
    private McpClientFactory mcpClientFactory;

    @Autowired
    private com.mio.ai.resource.service.mcp.McpStdioGuard mcpStdioGuard;

    /** 缓存键为 "mcpId|配置指纹"（字符串），同一 MCP 的不同覆盖配置各占一项 */
    private final ConcurrentHashMap<String, McpClientFactory.McpClientHandle> clientCache = new ConcurrentHashMap<>();

    /** 缓存上限：超过整体清空重建，避免配置频繁变更导致连接无限累积 */
    private static final int CLIENT_CACHE_MAX = 64;

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

    /** 配置节点是否为 STDIO（含 command 且 URL 为空） */
    static boolean isStdio(JSONObject server) {
        if (server == null) {
            return false;
        }
        String command = server.getStr("command");
        String url = server.getStr("url");
        return command != null && !command.isBlank() && (url == null || url.isBlank());
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
                    // STDIO 门禁：禁止用覆盖配置把「原本不是 STDIO」的工具改成执行本地命令
                    // （这正是绕过创建/绑定入口校验注入 RCE 的路径）。
                    // 原本就是 STDIO 的配置不受影响，避免让存量工具失效。
                    if ("command".equals(key) && !isStdio(baseServer)) {
                        String denyReason = mcpStdioGuard.denyReason(override.toString(), null);
                        if (denyReason != null) {
                            log.warn("绑定覆盖配置试图把 HTTP 型 MCP 改为 STDIO，已拒绝: {}", serverName);
                            return baseConfig;
                        }
                    }
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
    public ToolCallback[] getPublicMcpToolCallbacks() {
        return getPublicMcpToolsDetailed().callbacks();
    }

    @Override
    public McpInitResult getPublicMcpToolsDetailed() {
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpTool::getIsPublic, 1)
                .eq(McpTool::getStatus, 1);
        List<McpTool> publicTools = mcpToolMapper.selectList(wrapper);
        if (publicTools.isEmpty()) {
            return McpInitResult.empty();
        }
        return initMcpToolsDetailed(publicTools);
    }

    @Override
    public ToolCallback[] initMcpToolCallbacks(List<McpTool> mcpTools) {
        return initMcpToolsDetailed(mcpTools).callbacks();
    }

    @Override
    public McpInitResult initMcpToolsDetailed(List<McpTool> mcpTools) {
        if (mcpTools == null || mcpTools.isEmpty()) {
            return McpInitResult.empty();
        }

        List<ToolCallback> allCallbacks = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        // 跨服务器去重：两个 MCP 暴露同名子工具时后者加 id 后缀，避免同名 ToolCallback 一起传给模型
        Set<String> usedNames = new HashSet<>();

        for (McpTool mcpTool : mcpTools) {
            String cacheKey = cacheKeyOf(mcpTool);
            try {
                // 复用缓存的客户端，避免每次对话都新建连接（旧实现每次 put 覆盖且不 close，造成进程/连接泄漏）。
                // 缓存键含配置指纹：绑定级 configOverride 合并后会改变端点/密钥/命令，
                // 只按 mcpId 缓存会让后绑定者拿到前一个的客户端（配置不生效且可能用错凭证）
                McpClientFactory.McpClientHandle handle = clientCache.get(cacheKey);
                if (handle == null) {
                    handle = createHandle(mcpTool);
                    if (handle == null) {
                        failures.add(mcpTool.getName() + "：配置无效");
                        continue;
                    }
                    cacheHandle(cacheKey, handle);
                }
                allCallbacks.addAll(buildCallbacks(mcpTool, handle, usedNames));
                log.info("成功初始化MCP工具: {} - {}", mcpTool.getName(), mcpTool.getId());
            } catch (Exception e) {
                // 缓存的客户端可能已失效（服务端重启等）：关闭并重建一次
                closeQuietly(clientCache.remove(cacheKey));
                try {
                    McpClientFactory.McpClientHandle recreated = createHandle(mcpTool);
                    if (recreated != null) {
                        cacheHandle(cacheKey, recreated);
                        allCallbacks.addAll(buildCallbacks(mcpTool, recreated, usedNames));
                        log.info("重建MCP客户端成功: {} - {}", mcpTool.getName(), mcpTool.getId());
                        continue;
                    }
                } catch (Exception retryError) {
                    closeQuietly(clientCache.remove(cacheKey));
                }
                log.error("初始化MCP工具失败: {} - {}", mcpTool.getName(), mcpTool.getId(), e);
                failures.add(mcpTool.getName() + "：" + shortReason(e));
            }
        }

        return new McpInitResult(allCallbacks.toArray(new ToolCallback[0]), List.copyOf(failures));
    }

    /** 缓存键：mcpId + 生效配置指纹（同一 MCP 在不同智能体上覆盖成不同端点时互不串用） */
    private static String cacheKeyOf(McpTool mcpTool) {
        String config = mcpTool.getConfig() == null ? "" : mcpTool.getConfig();
        return mcpTool.getId() + "|" + Integer.toHexString(config.hashCode());
    }

    /** 缓存客户端；超过上限整体清空重建（供应商配置编辑后旧实例靠此淘汰） */
    private void cacheHandle(String cacheKey, McpClientFactory.McpClientHandle handle) {
        if (clientCache.size() >= CLIENT_CACHE_MAX) {
            closeAllClients();
        }
        McpClientFactory.McpClientHandle previous = clientCache.put(cacheKey, handle);
        if (previous != null && previous != handle) {
            closeQuietly(previous);
        }
    }

    /** 失败原因摘要：取首行、截断，供会话内提示（完整堆栈只在日志） */
    /**
     * 失败原因的用户可读描述：MCP SDK 的原文（如 "Client failed to initialize by explicit API call"）
     * 对用户没有指导意义——这里按失败类型归类成中文原因，完整信息仍留在日志。
     */
    static String shortReason(Exception e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        String lower = raw.toLowerCase();
        // 连接类
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return "连接超时，服务未响应";
        }
        if (lower.contains("connection refused") || lower.contains("connectException".toLowerCase())) {
            return "连接被拒绝，服务可能未启动";
        }
        if (lower.contains("unknownhost") || lower.contains("unknown host")
                || lower.contains("nodename nor servname")) {
            return "域名解析失败，请检查地址";
        }
        if (lower.contains("ssl") || lower.contains("certificate") || lower.contains("handshake")) {
            return "TLS 握手失败，证书或协议不兼容";
        }
        // 认证类
        if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("invalid token")) {
            return "认证失败，请检查访问令牌";
        }
        if (lower.contains("403") || lower.contains("forbidden")) {
            return "无权访问该服务";
        }
        if (lower.contains("404")) {
            return "接口路径不存在，请检查 MCP 地址";
        }
        // 协议/握手类
        if (lower.contains("initialize") || lower.contains("initialize by explicit api call")) {
            return "握手失败，对方可能不是可用的 MCP 服务";
        }
        if (lower.contains("parse") || lower.contains("json") || lower.contains("schema")) {
            return "响应格式无法解析，对方可能不是标准 MCP 服务";
        }
        // 兜底：保留首行截断，避免整段 SDK 文本刷屏
        int newline = raw.indexOf('\n');
        String first = newline > 0 ? raw.substring(0, newline) : raw;
        if (first.isBlank()) {
            return "初始化失败（" + e.getClass().getSimpleName() + "）";
        }
        return first.length() > 80 ? first.substring(0, 80) + "..." : first;
    }

    /**
     * 把单个客户端的工具投影成带前缀的回调：mcp__<server>__<tool>（zcode 同款命名），
     * 段内非法字符（OpenAI 函数名只允许字母数字下划线连字符）替换为 _，总长截断到 64
     */
    private List<ToolCallback> buildCallbacks(McpTool mcpTool, McpClientFactory.McpClientHandle handle,
                                              Set<String> usedNames) throws IOException {
        String serverSegment = sanitizeSegment(firstServerKey(mcpTool.getConfig(), mcpTool.getName()));
        List<McpSchema.Tool> tools = handle.client().listTools().tools();
        List<ToolCallback> callbacks = new ArrayList<>(tools.size());
        for (McpSchema.Tool tool : tools) {
            String base = truncate("mcp__" + serverSegment + "__" + sanitizeSegment(tool.name()));
            String name = usedNames.add(base) ? base : truncate(base + "__" + mcpTool.getId());
            callbacks.add(SyncMcpToolCallback.builder()
                    .mcpClient(handle.client())
                    .tool(tool)
                    .prefixedToolName(name)
                    .toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter.defaultConverter())
                    .build());
        }
        return callbacks;
    }

    public static String sanitizeSegment(String segment) {
        String cleaned = segment == null ? "" : segment.replaceAll("[^a-zA-Z0-9_-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return cleaned.isEmpty() ? "srv" : cleaned;
    }

    static String truncate(String name) {
        return name.length() <= 64 ? name : name.substring(0, 64);
    }

    /** mcpServers 首个服务节点名作为前缀段（与配置展示一致）；解析失败回退实体名 */
    public static String firstServerKey(String config, String fallback) {
        try {
            JSONObject root = JSONUtil.parseObj(config);
            JSONObject servers = root.getJSONObject("mcpServers");
            if (servers != null && !servers.isEmpty()) {
                return servers.keySet().iterator().next();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    @Override
    public void evictClient(Long mcpId) {
        if (mcpId == null) {
            return;
        }
        // 缓存键带配置指纹：删除该 MCP 的全部派生实例（含各智能体覆盖配置的）
        String prefix = mcpId + "|";
        clientCache.keySet().removeIf(key -> {
            if (!key.startsWith(prefix)) {
                return false;
            }
            closeQuietly(clientCache.get(key));
            return true;
        });
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
        for (Map.Entry<String, McpClientFactory.McpClientHandle> entry : clientCache.entrySet()) {
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
