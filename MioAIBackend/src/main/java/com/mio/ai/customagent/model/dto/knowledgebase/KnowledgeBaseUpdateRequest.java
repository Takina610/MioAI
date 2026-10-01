package com.mio.ai.customagent.model.dto.knowledgebase;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 知识库更新请求
 */
@Data
public class KnowledgeBaseUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 知识库ID
     */
    @NotNull(message = "知识库ID不能为空")
    private Long id;

    /**
     * 知识库名称
     */
    @Size(max = 100, message = "知识库名称长度不能超过100")
    @XssClean(mode = "strict")
    private String name;

    /**
     * 知识库描述
     */
    @Size(max = 2000, message = "知识库描述长度不能超过2000")
    @XssClean(mode = "rich")
    private String description;

    /**
     * 状态
     */
    private Integer isPublic;
}
