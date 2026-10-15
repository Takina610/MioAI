package com.mio.ai.admin.model.dto;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员创建知识库请求：空库壳（文档由知识库详情页继续管理），归属当前管理员
 */
@Data
public class KnowledgeAdminCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 100, message = "知识库名称长度不能超过100")
    @XssClean(mode = "strict")
    private String name;

    @Size(max = 2000, message = "知识库描述长度不能超过2000")
    @XssClean(mode = "rich")
    private String description;

    /** 0 私有 / 1 公开，缺省 0 */
    private Integer isPublic;

    /** 分块大小（token），缺省走系统默认 */
    private Integer chunkSize;

    /** 分块重叠（token） */
    private Integer chunkOverlap;
}
