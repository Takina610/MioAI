package com.mio.ai.customagent.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.customagent.mapper.McpToolMapper;
import com.mio.ai.customagent.model.dto.mcptool.McpToolAddRequest;
import com.mio.ai.customagent.model.dto.mcptool.McpToolQueryRequest;
import com.mio.ai.customagent.model.dto.mcptool.McpToolUpdateRequest;
import com.mio.ai.customagent.model.entity.McpTool;
import com.mio.ai.customagent.model.enums.McpToolStatusEnum;
import com.mio.ai.customagent.model.vo.McpToolVO;
import com.mio.ai.customagent.service.McpToolService;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: MCP工具服务实现类
 */
@Service
public class McpToolServiceImpl extends ServiceImpl<McpToolMapper, McpTool> implements McpToolService {

    @Override
    public Long addMcpTool(McpToolAddRequest request, Long userId) {
        if (StringUtils.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "工具名称不能为空");
        }
        if (StringUtils.isBlank(request.getServerName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "服务器名称不能为空");
        }
        McpTool mcpTool = new McpTool();
        BeanUtil.copyProperties(request, mcpTool);
        mcpTool.setUserId(userId);
        mcpTool.setStatus(McpToolStatusEnum.ACTIVE.getCode());
        mcpTool.setUsageCount(0);
        mcpTool.setIsPublic(request.getIsPublic() != null ? request.getIsPublic() : 0);
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
        BeanUtil.copyProperties(request, mcpTool);
        return this.updateById(mcpTool);
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
        return this.removeById(id);
    }

    @Override
    public McpToolVO getMcpToolById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "工具ID不能为空");
        }
        McpTool mcpTool = this.getById(id);
        if (mcpTool == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "MCP工具不存在");
        }
        return convertToVO(mcpTool);
    }

    @Override
    public Page<McpToolVO> queryMcpTools(McpToolQueryRequest request) {
        Page<McpTool> page = new Page<>(request.getCurrent(), request.getPageSize());
        LambdaQueryWrapper<McpTool> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(request.getName()), McpTool::getName, request.getName())
                .eq(StringUtils.isNotBlank(request.getServerName()), McpTool::getServerName, request.getServerName())
                .eq(request.getStatus() != null, McpTool::getStatus, request.getStatus())
                .eq(request.getIsPublic() != null, McpTool::getIsPublic, request.getIsPublic())
                .eq(request.getUserId() != null, McpTool::getUserId, request.getUserId())
                .orderByDesc(McpTool::getCreateTime);
        Page<McpTool> toolPage = this.page(page, wrapper);
        Page<McpToolVO> voPage = new Page<>(toolPage.getCurrent(), toolPage.getSize(), toolPage.getTotal());
        voPage.setRecords(toolPage.getRecords().stream().map(this::convertToVO).toList());
        return voPage;
    }

    @Override
    public void incrementUsageCount(Long id) {
        McpTool mcpTool = this.getById(id);
        if (mcpTool != null) {
            mcpTool.setUsageCount(mcpTool.getUsageCount() + 1);
            mcpTool.setLastUsedTime(new Date());
            this.updateById(mcpTool);
        }
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
        McpToolVO vo = new McpToolVO();
        BeanUtil.copyProperties(mcpTool, vo);
        McpToolStatusEnum statusEnum = McpToolStatusEnum.getByCode(mcpTool.getStatus());
        vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
        return vo;
    }
}
