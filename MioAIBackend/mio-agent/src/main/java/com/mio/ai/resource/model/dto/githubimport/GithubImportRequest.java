package com.mio.ai.resource.model.dto.githubimport;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 文件导入请求
 */
@Data
public class GithubImportRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 知识库ID
     */
    @NotNull(message = "知识库ID不能为空")
    private Long kbId;

    /**
     * GitHub 仓库 / 目录 / 文件链接（与预览时一致）
     */
    @NotBlank(message = "请输入 GitHub 链接")
    @Size(max = 2000, message = "链接过长")
    @XssClean(mode = "strict")
    private String url;

    /**
     * 待导入的仓库内文件路径列表（来自预览结果）
     */
    @NotEmpty(message = "请选择要导入的文件")
    @Size(max = 50, message = "单次最多导入 50 个文件")
    private List<String> paths;
}
