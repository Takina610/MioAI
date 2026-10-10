package com.mio.ai.resource.model.vo.knowledge;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 导入预览结果
 */
@Data
public class GithubPreviewVO implements Serializable {

    private String owner;

    private String repo;

    /**
     * 解析出的分支
     */
    private String branch;

    /**
     * 命中可导入规则的文件数（files 截断前的总数）
     */
    private long totalMatched;

    /**
     * 预览列表是否因超出上限被截断
     */
    private boolean truncated;

    /**
     * 因扩展名不支持被跳过的文件数
     */
    private long skippedUnsupported;

    /**
     * 因超过单文件大小上限被跳过的文件数
     */
    private long skippedTooLarge;

    private List<GithubFileInfoVO> files;
}
