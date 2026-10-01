package com.mio.ai.customagent.service.mcp.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.customagent.mapper.mcp.McpToolMapper;
import com.mio.ai.customagent.mapper.agent.AgentMcpMapper;
import com.mio.ai.customagent.model.dto.mcptool.McpToolAddRequest;
import com.mio.ai.customagent.model.dto.mcptool.McpToolQueryRequest;
import com.mio.ai.customagent.model.dto.mcptool.McpToolUpdateRequest;
import com.mio.ai.customagent.model.entity.AgentMcp;
import com.mio.ai.customagent.model.entity.McpTool;
import com.mio.ai.customagent.model.enums.McpToolStatusEnum;
import com.mio.ai.customagent.model.vo.mcp.McpToolVO;
import com.mio.ai.customagent.service.mcp.McpClientManagerService;
import com.mio.ai.customagent.service.mcp.McpToolService;
import com.mio.ai.customagent.service.mcp.impl.McpClientFactory;
import com.mio.ai.user.mapper.UserMapper;
import com.mio.ai.user.model.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
        McpTool mcpTool = new McpTool();
        BeanUtil.copyProperties(request, mcpTool);
        mcpTool.setUserId(userId);
        mcpTool.setStatus(McpToolStatusEnum.ACTIVE.getCode());
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
        // 删除智能体与该 MCP 的关联记录，并关闭其缓存客户端
        agentMcpMapper.delete(new LambdaQueryWrapper<AgentMcp>().eq(AgentMcp::getMcpId, id));
        mcpClientManagerService.evictClient(id);
        return this.removeById(id);
    }

    @Override
    public McpToolVO getMcpToolById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "工具ID不能为空");
        }
        McpTool mcpTool = this.getById(id);
        if (mcpTool == null) {
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "MCP工具不存在");
            return null;
        }
        return convertToVO(mcpTool);
    }

    @Override
    public Page<McpToolVO> queryMcpTools(McpToolQueryRequest request) {
        Page<McpTool> page = new Page<>(request.getCurrent(), request.getPageSize());
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getStatus() != null, McpTool::getStatus, request.getStatus())
                .eq(request.getIsPublic() != null, McpTool::getIsPublic, request.getIsPublic())
                .eq(request.getUserId() != null, McpTool::getUserId, request.getUserId())
                .orderByDesc(McpTool::getCreateTime);
        Page<McpTool> toolPage = this.page(page, wrapper);
        Page<McpToolVO> voPage = new Page<>(toolPage.getCurrent(), toolPage.getSize(), toolPage.getTotal());
        voPage.setRecords(toolPage.getRecords().stream().map(this::convertToVO).toList());
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
        voPage.setRecords(toolPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    private McpToolVO convertToVO(McpTool mcpTool) {
        User user = userMapper.selectById(mcpTool.getUserId());

        McpToolVO vo = new McpToolVO();
        vo.setUserName(user.getUserName());

        BeanUtil.copyProperties(mcpTool, vo);
        McpToolStatusEnum statusEnum = McpToolStatusEnum.getByCode(mcpTool.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        return vo;
    }
}
