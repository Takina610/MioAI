package com.mio.ai.customagent.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mio.ai.customagent.model.dto.mcptool.McpToolAddRequest;
import com.mio.ai.customagent.model.dto.mcptool.McpToolQueryRequest;
import com.mio.ai.customagent.model.dto.mcptool.McpToolUpdateRequest;
import com.mio.ai.customagent.model.dto.mcptool.McpValidateRequest;
import com.mio.ai.customagent.model.entity.McpTool;
import com.mio.ai.customagent.model.vo.McpToolVO;
import com.mio.ai.customagent.model.vo.McpValidateResultVO;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: MCP工具服务接口
 */
public interface McpToolService extends IService<McpTool> {

    /**
     * 创建MCP工具
     */
    Long addMcpTool(McpToolAddRequest request, Long userId);

    /**
     * 更新MCP工具
     */
    boolean updateMcpTool(McpToolUpdateRequest request, Long userId);

    /**
     * 删除MCP工具
     */
    boolean deleteMcpTool(Long id, Long userId);

    /**
     * 根据ID获取MCP工具
     */
    McpToolVO getMcpToolById(Long id);

    /**
     * 分页查询MCP工具
     */
    Page<McpToolVO> queryMcpTools(McpToolQueryRequest request);

    /**
     * 获取公开的MCP工具列表（广场）
     */
    Page<McpToolVO> getPublicMcpTools(long current, long size);
}
