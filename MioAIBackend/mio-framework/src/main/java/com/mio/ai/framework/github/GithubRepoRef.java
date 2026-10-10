package com.mio.ai.framework.github;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 链接解析结果。restSegments 是 tree/blob 之后剩余的路径段，
 *               分支名可能含斜杠（feature/x），ref 与路径的切分交由服务层按 GitHub API 探测消歧。
 */
public record GithubRepoRef(String owner, String repo, Kind kind, List<String> restSegments) {

    public enum Kind {
        /** 整个仓库（默认分支） */
        REPO,
        /** 指定分支 / 目录 */
        TREE,
        /** 单个文件 */
        BLOB
    }
}
