package com.mio.ai.customagent.model.dto.knowledgebase;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 知识库创建请求
 */
@Data
public class KnowledgeBaseAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 知识库名称
     */
    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称长度不能超过100")
    @XssClean(mode = "strict")
    private String name;

    /**
     * 知识库描述
     */
    @Size(max = 2000, message = "知识库描述长度不能超过2000")
    @XssClean(mode = "rich")
    private String description;
}
