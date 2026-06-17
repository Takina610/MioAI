package com.mio.ai.customagent.model.dto.mcptool;

import com.mio.ai.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: MCP工具查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class McpToolQueryRequest extends PageRequest implements Serializable {
    /**
     * 工具名称（模糊搜索）
     */
    private String name;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 是否公开
     */
    private Integer isPublic;

    /**
     * 用户ID
     */
    private Long userId;
}
