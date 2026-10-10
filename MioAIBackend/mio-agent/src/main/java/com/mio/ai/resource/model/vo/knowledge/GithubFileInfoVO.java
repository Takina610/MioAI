package com.mio.ai.resource.model.vo.knowledge;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 仓库内可导入文件视图对象
 */
@Data
public class GithubFileInfoVO implements Serializable {

    private String path;

    /**
     * 文件大小（字节）
     */
    private long size;
}
