package com.mio.ai.resource.model.dto.githubimport;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 导入预览请求
 */
@Data
public class GithubImportPreviewRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * GitHub 仓库 / 目录 / 文件链接
     */
    @NotBlank(message = "请输入 GitHub 链接")
    @Size(max = 2000, message = "链接过长")
    @XssClean(mode = "strict")
    private String url;
}
