package com.mio.ai.resource.service.agent.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.framework.mcp.McpClientFactory;
import com.mio.ai.resource.mapper.agent.AgentMcpMapper;
import com.mio.ai.resource.model.dto.agentmcp.AgentMcpAddRequest;
import com.mio.ai.resource.model.entity.AgentMcp;
import com.mio.ai.resource.service.agent.AgentMcpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 智能体-MCP关联服务实现类
 */
@Service
public class AgentMcpServiceImpl extends ServiceImpl<AgentMcpMapper, AgentMcp> implements AgentMcpService {

    @Autowired
    private McpClientFactory mcpClientFactory;

    @Autowired
    private com.mio.ai.resource.service.mcp.McpStdioGuard mcpStdioGuard;

    /** 读取 MCP 原始配置用于 STDIO 判定（不引入 McpToolService，避免依赖成环） */
    @Autowired
    private com.mio.ai.resource.mapper.mcp.McpToolMapper mcpToolMapper;

    @Override
    public Long addAgentMcp(AgentMcpAddRequest request) {
        return addAgentMcp(request, null);
    }

    /**
     * @param userId 绑定操作者：覆盖配置可能改写 command（STDIO 会在服务器上执行命令），
     *               必须与新建 MCP 工具走同一 stdio 门禁，否则可绕过限制实现远程命令执行
     */
    @Override
    public Long addAgentMcp(AgentMcpAddRequest request, Long userId) {
        if (request.getAgentId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID不能为空");
        }
        if (request.getMcpId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "MCP工具ID不能为空");
        }
        if (StringUtils.isNotBlank(request.getConfigOverride())) {
            // 绑定时就拒绝坏覆盖配置：否则运行时合并失败会静默回退原配置，用户毫无感知
            String structureError = mcpClientFactory.validateStructure(request.getConfigOverride());
            if (structureError != null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "配置覆盖无效：" + structureError);
            }
            // STDIO 门禁：只拦「把原本不是 STDIO 的工具改成执行本地命令」这一越权路径。
            // 原本就是 STDIO 的工具（可能由管理员创建）允许普通用户继续使用/调 env。
            if (introducesStdio(request.getMcpId(), request.getConfigOverride(), userId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR,
                        "STDIO 型 MCP 会在服务器上执行本地命令，当前仅对管理员开放");
            }
        }
        AgentMcp agentMcp = new AgentMcp();
        agentMcp.setAgentId(request.getAgentId());
        agentMcp.setMcpId(request.getMcpId());
        agentMcp.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        agentMcp.setConfigOverride(request.getConfigOverride());
        this.save(agentMcp);
        return agentMcp.getId();
    }

    /**
     * 覆盖配置是否把「原本非 STDIO」的工具改成了 STDIO。
     * <p>这是唯一的越权形态：用户本来无权创建 STDIO（会执行本地命令），
     * 却能借 HTTP 型工具 + 覆盖 command 绕到等价效果。
     * 原本就是 STDIO 的工具不受影响（存量可用性），无覆盖配置也不受影响。
     */
    private boolean introducesStdio(Long mcpId, String configOverride, Long userId) {
        if (mcpStdioGuard == null) {
            return false;
        }
        com.mio.ai.resource.model.entity.McpTool base = mcpId == null ? null : this.getBaseMcp(mcpId);
        if (base != null && isStdioConfig(base.getConfig())) {
            return false;
        }
        if (!isStdioConfig(configOverride)) {
            return false;
        }
        return mcpStdioGuard.denyReason(configOverride, userId) != null;
    }

    /** 读取 MCP 原始配置（避免与 McpToolService 循环依赖，直接用 mapper 查询） */
    private com.mio.ai.resource.model.entity.McpTool getBaseMcp(Long mcpId) {
        return mcpToolMapper.selectById(mcpId);
    }

    /** 配置是否 STDIO 形态（有 command 且无 url） */
    private static boolean isStdioConfig(String config) {
        if (StringUtils.isBlank(config)) {
            return false;
        }
        try {
            cn.hutool.json.JSONObject root = cn.hutool.json.JSONUtil.parseObj(config);
            cn.hutool.json.JSONObject servers = root.getJSONObject("mcpServers");
            if (servers == null || servers.isEmpty()) {
                return false;
            }
            cn.hutool.json.JSONObject server =
                    servers.getJSONObject(servers.keySet().iterator().next());
            if (server == null) {
                return false;
            }
            String command = server.getStr("command");
            String url = server.getStr("url");
            return command != null && !command.isBlank() && StringUtils.isBlank(url);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean deleteAgentMcp(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "关联ID不能为空");
        }
        return this.removeById(id);
    }

    @Override
    public boolean deleteByAgentIdAndMcpId(Long agentId, Long mcpId) {
        if (agentId == null || mcpId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "智能体ID和MCP工具ID不能为空");
        }
        LambdaQueryWrapper<AgentMcp> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMcp::getAgentId, agentId)
                .eq(AgentMcp::getMcpId, mcpId);
        return this.remove(wrapper);
    }

    @Override
    public List<Long> getMcpIdsByAgentId(Long agentId) {
        LambdaQueryWrapper<AgentMcp> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMcp::getAgentId, agentId)
                .eq(AgentMcp::getEnabled, 1);
        List<AgentMcp> list = this.list(wrapper);
        return list.stream().map(AgentMcp::getMcpId).toList();
    }

    @Override
    public boolean deleteByAgentId(Long agentId) {
        LambdaQueryWrapper<AgentMcp> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentMcp::getAgentId, agentId);
        return this.remove(wrapper);
    }
}
