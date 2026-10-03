package com.mio.ai.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.admin.model.dto.McpAdminUpdateRequest;
import com.mio.ai.common.aop.annotation.AuthCheck;
import com.mio.ai.common.aop.annotation.LogInfo;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.constant.UserConstant;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.exception.ThrowUtils;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.resource.model.dto.mcptool.McpToolQueryRequest;
import com.mio.ai.resource.model.entity.McpTool;
import com.mio.ai.resource.model.enums.McpToolStatusEnum;
import com.mio.ai.resource.model.vo.mcp.McpToolVO;
import com.mio.ai.resource.service.mcp.McpToolService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员 MCP 工具管理接口
 */
@Validated
@Slf4j
@RestController
@RequestMapping("/admin/mcp")
public class McpAdminController {

    @Resource
    private McpToolService mcpToolService;

    /**
     * 分页查询 MCP 工具列表（管理员）
     */
    @PostMapping("/search")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Page<McpToolVO>> listMcpByPage(@RequestBody McpToolQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long current = request.getCurrent();
        long size = request.getPageSize();
        ThrowUtils.throwIf(size > 50, ErrorCode.PARAMS_ERROR, "每页数据量不能超过 50");

        QueryWrapper<McpTool> queryWrapper = new QueryWrapper<>();
        if (StrUtil.isNotBlank(request.getName())) {
            queryWrapper.like("name", request.getName());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq("status", request.getStatus());
        }
        if (request.getIsPublic() != null) {
            queryWrapper.eq("is_public", request.getIsPublic());
        }
        queryWrapper.orderByDesc("update_time");

        Page<McpTool> mcpPage = mcpToolService.page(new Page<>(current, size), queryWrapper);
        Page<McpToolVO> voPage = new Page<>(mcpPage.getCurrent(), mcpPage.getSize(), mcpPage.getTotal());
        List<McpToolVO> voList = mcpPage.getRecords().stream().map(mcp -> {
            McpToolVO vo = new McpToolVO();
            BeanUtil.copyProperties(mcp, vo);
            McpToolStatusEnum statusEnum = McpToolStatusEnum.getByCode(mcp.getStatus());
            vo.setStatusDesc(statusEnum != null ? statusEnum.getDesc() : "未知");
            return vo;
        }).toList();
        voPage.setRecords(voList);
        return ResultUtils.success(voPage);
    }

    /**
     * 根据 id 获取 MCP 工具详情
     */
    @GetMapping("/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<McpToolVO> getMcpById(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "MCP 工具 id 不合法");
        McpToolVO vo = mcpToolService.getMcpToolById(id);
        return ResultUtils.success(vo);
    }

    /**
     * 更新 MCP 工具信息（管理员）
     * 注意：config 和 toolInfo 不可修改
     */
    @PutMapping
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Boolean> updateMcp(@Valid @RequestBody McpAdminUpdateRequest request) {
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        McpTool mcpTool = mcpToolService.getById(request.getId());
        ThrowUtils.throwIf(mcpTool == null, ErrorCode.NOT_FOUND_ERROR, "MCP 工具不存在");

        McpTool update = new McpTool();
        update.setId(request.getId());
        if (request.getName() != null) {
            update.setName(request.getName());
        }
        if (request.getDescription() != null) {
            update.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            update.setStatus(request.getStatus());
        }
        if (request.getIsPublic() != null) {
            update.setIsPublic(request.getIsPublic());
        }
        boolean result = mcpToolService.updateById(update);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新失败");
        return ResultUtils.success(true);
    }

    /**
     * 删除 MCP 工具
     */
    @DeleteMapping("/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @LogInfo
    public BaseResponse<Boolean> deleteMcp(@PathVariable Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "MCP 工具 id 不合法");
        // 管理员删除，传入 userId 为 null 跳过权限校验
        // 直接删除，关联的 agent_mcp 记录已在 McpToolServiceImpl 中处理
        McpTool mcpTool = mcpToolService.getById(id);
        ThrowUtils.throwIf(mcpTool == null, ErrorCode.NOT_FOUND_ERROR, "MCP 工具不存在");
        boolean result = mcpToolService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除失败");
        return ResultUtils.success(true);
    }
}
