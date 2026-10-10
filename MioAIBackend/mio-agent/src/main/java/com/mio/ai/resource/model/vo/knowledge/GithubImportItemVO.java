package com.mio.ai.resource.model.vo.knowledge;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 单文件导入结果
 */
@Data
public class GithubImportItemVO implements Serializable {

    /**
     * 仓库内路径
     */
    private String path;

    /**
     * 生成的文档记录文件名
     */
    private String fileName;

    /**
     * 文档记录ID（失败时为 null）
     */
    private Long docId;

    /**
     * 文件大小（字节）
     */
    private long fileSize;

    /**
     * success / error
     */
    private String status;

    /**
     * 失败原因
     */
    private String error;
}
