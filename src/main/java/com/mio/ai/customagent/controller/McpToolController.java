package com.mio.ai.customagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.utils.RedisComponent;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.customagent.model.dto.mcptool.McpToolAddRequest;
import com.mio.ai.customagent.model.dto.mcptool.McpToolQueryRequest;
import com.mio.ai.customagent.model.dto.mcptool.McpToolUpdateRequest;
import com.mio.ai.customagent.model.dto.mcptool.McpValidateRequest;
import com.mio.ai.customagent.model.vo.McpToolVO;
import com.mio.ai.customagent.model.vo.McpValidateResultVO;
import com.mio.ai.customagent.service.McpToolService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: MCP工具接口
 */
@Validated
@Slf4j
@RestController
@RequestMapping("/mcp")
public class McpToolController {

    @Resource
    private McpToolService mcpToolService;

    @Resource
    private RedisComponent redisComponent;

    @PostMapping
    @CacheEvict(value = "mcpTools", allEntries = true)
    public BaseResponse<Long> addMcpTool(@Valid @RequestBody McpToolAddRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        Long id = mcpToolService.addMcpTool(request, userId);
        return ResultUtils.success(id);
    }

    @PutMapping
    @CacheEvict(value = "mcpTools", allEntries = true)
    public BaseResponse<Boolean> updateMcpTool(@Valid @RequestBody McpToolUpdateRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        boolean result = mcpToolService.updateMcpTool(request, userId);
        return ResultUtils.success(result);
    }

    @DeleteMapping("/{id:\\d+}")
    @CacheEvict(value = "mcpTools", allEntries = true)
    public BaseResponse<Boolean> deleteMcpTool(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        boolean result = mcpToolService.deleteMcpTool(id, userId);
        return ResultUtils.success(result);
    }

    @GetMapping("/market")
    @Cacheable(value = "mcpTools")
    public BaseResponse<Page<McpToolVO>> getMarketMcpTools(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        Page<McpToolVO> page = mcpToolService.getPublicMcpTools(current, size);
        return ResultUtils.success(page);
    }

    @GetMapping("/{id:\\d+}")
    @Cacheable(value = "mcpTools", key = "#id")
    public BaseResponse<McpToolVO> getMcpTool(@PathVariable Long id) {
        McpToolVO tool = mcpToolService.getMcpToolById(id);
        return ResultUtils.success(tool);
    }

    @PostMapping("/list")
    public BaseResponse<Page<McpToolVO>> listMcpTools(@RequestBody McpToolQueryRequest request, HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        request.setUserId(userId);
        Page<McpToolVO> page = mcpToolService.queryMcpTools(request);
        return ResultUtils.success(page);
    }
}
