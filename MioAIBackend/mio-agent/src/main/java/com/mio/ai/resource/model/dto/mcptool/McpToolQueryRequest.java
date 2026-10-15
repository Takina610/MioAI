package com.mio.ai.resource.model.dto.mcptool;

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

    /** 管理端多选状态过滤 */
    private java.util.List<Integer> statusList;

    /** 管理端按归属用户名模糊过滤 */
    private String ownerName;

    /**
     * 是否公开
     */
    private Integer isPublic;

    /**
     * 用户ID
     */
    private Long userId;
}
