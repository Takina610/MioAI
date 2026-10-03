package com.mio.ai.resource.model.dto.knowledgebase;

import com.mio.ai.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 知识库查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class KnowledgeBaseQueryRequest extends PageRequest implements Serializable {

    /**
     * 知识库名称（模糊查询）
     */
    private String name;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 用户ID
     */
    private Long userId;
}
