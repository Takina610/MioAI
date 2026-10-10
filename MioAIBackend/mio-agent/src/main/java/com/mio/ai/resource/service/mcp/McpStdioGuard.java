package com.mio.ai.resource.service.mcp;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.mio.ai.user.model.enums.UserRoleEnum;
import com.mio.ai.user.mapper.UserMapper;
import com.mio.ai.user.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * STDIO 型 MCP 门禁：STDIO 配置会在服务器上执行本地命令，多用户服务下默认只对管理员开放，
 * 可用 mio.ai.mcp.stdio-enabled=true 对全部登录用户开放（HTTP/SSE 型不受影响）。
 * zcode 是本地 CLI 无此问题，该门禁是 Web 化后的必要差异
 */
@Component
@Slf4j
public class McpStdioGuard {

    private final UserMapper userMapper;

    @Value("${mio.ai.mcp.stdio-enabled:false}")
    private boolean stdioEnabled;

    public McpStdioGuard(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 校验配置是否允许导入；返回拒绝原因，null 表示放行
     *
     * @param config mcpServers 完整配置 JSON；解析失败时返回 null（交给后续结构校验报错）
     * @param userId 当前用户，null 视为非管理员
     */
    public String denyReason(String config, Long userId) {
        if (StringUtils.isBlank(config)) {
            return null;
        }
        JSONObject serverConfig;
        try {
            JSONObject root = JSONUtil.parseObj(config);
            JSONObject servers = root.getJSONObject("mcpServers");
            if (servers == null || servers.isEmpty()) {
                return null;
            }
            serverConfig = servers.getJSONObject(servers.keySet().iterator().next());
        } catch (Exception e) {
            return null;
        }
        if (serverConfig == null || StringUtils.isBlank(serverConfig.getStr("command"))) {
            return null;
        }
        if (stdioEnabled || isAdmin(userId)) {
            return null;
        }
        log.info("STDIO 型 MCP 被门禁拦截: userId={}", userId);
        return "STDIO 型 MCP 会在服务器上执行本地命令，当前仅对管理员开放";
    }

    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }
}
