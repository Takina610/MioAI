package com.mio.ai.resource.model.dto.document;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 文档创建请求
 */
@Data
public class DocumentAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 知识库ID
     */
    @NotNull(message = "知识库ID不能为空")
    private Long kbId;

    /**
     * 文件名
     */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过255")
    @XssClean(mode = "strict")
    private String fileName;

    /**
     * 文件类型
     */
    @Size(max = 50, message = "文件类型长度不能超过50")
    private String fileType;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 文件路径
     */
    @Size(max = 500, message = "文件路径长度不能超过500")
    private String filePath;
}
