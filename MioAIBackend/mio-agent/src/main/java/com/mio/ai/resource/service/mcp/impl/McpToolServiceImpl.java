package com.mio.ai.resource.service.mcp.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.resource.mapper.agent.AgentMcpMapper;
import com.mio.ai.resource.mapper.mcp.McpToolMapper;
import com.mio.ai.resource.service.mcp.McpStdioGuard;
import com.mio.ai.resource.model.dto.mcp.McpToolAddRequest;
import com.mio.ai.resource.model.dto.mcp.McpToolQueryRequest;
import com.mio.ai.resource.model.dto.mcp.McpToolUpdateRequest;
import com.mio.ai.resource.model.entity.agent.AgentMcp;
import com.mio.ai.resource.model.entity.mcp.McpTool;
import com.mio.ai.resource.model.enums.mcp.McpToolStatusEnum;
import com.mio.ai.resource.model.vo.mcp.McpToolVO;
import com.mio.ai.resource.service.mcp.McpClientManagerService;
import com.mio.ai.resource.service.mcp.McpToolService;
import com.mio.ai.framework.mcp.McpClientFactory;
import com.mio.ai.user.mapper.UserMapper;
import com.mio.ai.user.model.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: MCP工具服务实现类
 */

@Service
public class McpToolServiceImpl extends ServiceImpl<McpToolMapper, McpTool> implements McpToolService {

    @Autowired
    UserMapper userMapper;

    @Autowired
    AgentMcpMapper agentMcpMapper;

    @Autowired
    McpClientFactory mcpClientFactory;

    @Autowired
    McpClientManagerService mcpClientManagerService;

    @Autowired
    McpStdioGuard mcpStdioGuard;

    @Override
    public Long addMcpTool(McpToolAddRequest request, Long userId) {
        if (StringUtils.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "工具名称不能为空");
        }
        // 服务端兜底校验配置结构：前端绕过校验直接调添加接口时，避免存入运行时必然失败的配置
        String configError = mcpClientFactory.validateStructure(request.getConfig());
        if (configError != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, configError);
        }
        String denyReason = mcpStdioGuard.denyReason(request.getConfig(), userId);
        if (denyReason != null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, denyReason);
        }
        McpTool mcpTool = new McpTool();
        BeanUtil.copyProperties(request, mcpTool);
        mcpTool.setUserId(userId);
        mcpTool.setStatus(McpToolStatusEnum.ACTIVE.getCode());
        // tool_info 列 NOT NULL 且无默认值：调用方不传时插库会直接失败（500），
        // 未连接过的工具本就还没有工具清单，兜底为空数组
        if (mcpTool.getToolInfo() == null || mcpTool.getToolInfo().isBlank()) {
            mcpTool.setToolInfo("[]");
        }
        this.save(mcpTool);
        return mcpTool.getId();
    }

    @Override
    public boolean updateMcpTool(McpToolUpdateRequest request, Long userId) {
        if (request.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "工具ID不能为空");
        }
        McpTool mcpTool = this.getById(request.getId());
        if (mcpTool == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "MCP工具不存在");
        }
        if (!mcpTool.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改该工具");
        }
        if (StringUtils.isNotBlank(request.getConfig())) {
            String configError = mcpClientFactory.validateStructure(request.getConfig());
            if (configError != null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, configError);
            }
            String denyReason = mcpStdioGuard.denyReason(request.getConfig(), userId);
            if (denyReason != null) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, denyReason);
            }
        }
        // 忽略 null 字段：部分更新时不能把未传字段清空（如只改名称会把 config 置空）
        BeanUtil.copyProperties(request, mcpTool, CopyOptions.create().setIgnoreNullValue(true));
        mcpTool.setUpdateTime(new Date());
        boolean updated = this.updateById(mcpTool);
        // 配置/信息变更后失效缓存客户端，下次对话按新配置重建
        mcpClientManagerService.evictClient(mcpTool.getId());
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMcpTool(Long id, Long userId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "工具ID不能为空");
        }
        McpTool mcpTool = this.getById(id);
        if (mcpTool == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "MCP工具不存在");
        }
        if (!mcpTool.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除该工具");
        }
        // 绑定删除与本体删除同一事务，避免删一半留下引用悬空
        agentMcpMapper.delete(new LambdaQueryWrapper<AgentMcp>().eq(AgentMcp::getMcpId, id));
        boolean removed = this.removeById(id);
        // 关闭缓存客户端（外部资源操作，失败不影响已提交的删除）
        try {
            mcpClientManagerService.evictClient(id);
        } catch (Exception e) {
            // 客户端失活由下次装配自然重建，这里只记日志
        }
        return removed;
    }

    @Override
    public McpToolVO getMcpToolById(Long id) {
        return getMcpToolById(id, null);
    }

    /**
     * 按查看者返回详情：所有者看到原始配置（编辑页需要回填），其他人只看脱敏配置。
     */
    @Override
    public McpToolVO getMcpToolById(Long id, Long viewerUserId) {
        return getMcpToolById(id, viewerUserId, false);
    }

    @Override
    public McpToolVO getMcpToolById(Long id, Long viewerUserId, boolean includeSecrets) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "工具ID不能为空");
        }
        McpTool mcpTool = this.getById(id);
        if (mcpTool == null) {
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "MCP工具不存在");
            return null;
        }
        return convertToVO(mcpTool, shouldMaskSecrets(viewerUserId, mcpTool.getUserId(), includeSecrets));
    }

    /**
     * 是否脱敏：所有者本人不脱敏；管理端审阅（includeSecrets）不脱敏；其余一律脱敏。
     * 抽成静态方法以便回归测试锁定「管理端仍能拿到原始配置」这一行为。
     */
    static boolean shouldMaskSecrets(Long viewerUserId, Long ownerUserId, boolean includeSecrets) {
        if (includeSecrets) {
            return false;
        }
        return viewerUserId == null || !viewerUserId.equals(ownerUserId);
    }

    @Override
    public Page<McpToolVO> queryMcpTools(McpToolQueryRequest request) {
        Page<McpTool> page = new Page<>(request.getCurrent(), request.getPageSize());
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(org.springframework.util.StringUtils.hasText(request.getName()),
                        McpTool::getName, request.getName())
                .eq(request.getStatus() != null, McpTool::getStatus, request.getStatus())
                .eq(request.getIsPublic() != null, McpTool::getIsPublic, request.getIsPublic())
                .eq(request.getUserId() != null, McpTool::getUserId, request.getUserId())
                .orderByDesc(McpTool::getCreateTime);
        Page<McpTool> toolPage = this.page(page, wrapper);
        Page<McpToolVO> voPage = new Page<>(toolPage.getCurrent(), toolPage.getSize(), toolPage.getTotal());
        // 列表查询按 userId 限定为本人资源，配置原样返回（编辑/详情需回填）
        voPage.setRecords(toolPage.getRecords().stream().map(tool -> convertToVO(tool, false)).toList());
        return voPage;
    }

    @Override
    public Page<McpToolVO> getPublicMcpTools(long current, long size) {
        Page<McpTool> page = new Page<>(current, size);
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(McpTool::getIsPublic, 1)
                .eq(McpTool::getStatus, McpToolStatusEnum.ACTIVE.getCode())
                .orderByDesc(McpTool::getCreateTime);
        Page<McpTool> toolPage = this.page(page, wrapper);
        Page<McpToolVO> voPage = new Page<>(toolPage.getCurrent(), toolPage.getSize(), toolPage.getTotal());
        // 广场对所有人（含匿名）开放：配置一律脱敏
        voPage.setRecords(toolPage.getRecords().stream().map(tool -> convertToVO(tool, true)).toList());
        return voPage;
    }

    private McpToolVO convertToVO(McpTool mcpTool, boolean maskSecrets) {
        User user = mcpTool.getUserId() == null ? null : userMapper.selectById(mcpTool.getUserId());

        McpToolVO vo = new McpToolVO();
        vo.setUserName(user == null ? null : user.getUserName());

        BeanUtil.copyProperties(mcpTool, vo);
        if (maskSecrets) {
            // 对外脱敏：headers/env 凭证、url query 与 args 里的密钥一律掩码
            vo.setConfig(com.mio.ai.framework.mcp.McpConfigMasker.mask(mcpTool.getConfig()));
        }
        McpToolStatusEnum statusEnum = McpToolStatusEnum.getByCode(mcpTool.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        return vo;
    }
}
