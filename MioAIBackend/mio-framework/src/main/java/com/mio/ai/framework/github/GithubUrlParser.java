package com.mio.ai.framework.github;

import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 仓库链接解析（纯语法层，不访问网络）。
 *               支持：/owner/repo、/owner/repo.git、/tree/{ref}/{subpath}、/blob/{ref}/{file}；
 *               http 自动升级 https，忽略 query 与 fragment。只接受 github.com 域名。
 */
public final class GithubUrlParser {

    private static final String ALLOWED_HOST = "github.com";
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    /** 单段路径长度上限，与 document.file_name 的 varchar(255) 对齐 */
    private static final int MAX_PATH_LENGTH = 255;

    private GithubUrlParser() {
    }

    public static GithubRepoRef parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入 GitHub 链接");
        }
        String url = rawUrl.trim();
        if (url.startsWith("http://")) {
            url = "https://" + url.substring(7);
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "链接格式不正确");
        }
        if (!ALLOWED_HOST.equalsIgnoreCase(uri.getHost())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 github.com 仓库链接");
        }

        String path = uri.getPath();
        if (path == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "链接缺少仓库路径");
        }
        List<String> segments = new ArrayList<>(Arrays.asList(path.split("/")));
        segments.removeIf(String::isEmpty);
        if (segments.size() < 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "链接需包含 owner/仓库两部分");
        }

        String owner = segments.get(0);
        String repo = segments.get(1);
        if (repo.toLowerCase().endsWith(".git")) {
            repo = repo.substring(0, repo.length() - 4);
        }
        validateName(owner, "owner");
        validateName(repo, "仓库名");

        GithubRepoRef.Kind kind = GithubRepoRef.Kind.REPO;
        List<String> rest = List.of();
        if (segments.size() > 2) {
            kind = switch (segments.get(2)) {
                case "tree" -> GithubRepoRef.Kind.TREE;
                case "blob" -> GithubRepoRef.Kind.BLOB;
                default -> throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "仅支持仓库主页、/tree/ 目录、/blob/ 文件三种链接");
            };
            if (segments.size() < 4) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "链接缺少分支或路径");
            }
            rest = segments.subList(3, segments.size());
            validateSegments(rest);
        }
        return new GithubRepoRef(owner, repo, kind, rest);
    }

    private static void validateName(String name, String label) {
        if (!NAME_PATTERN.matcher(name).matches() || name.contains("..")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "链接 " + label + " 不合法");
        }
    }

    /**
     * 路径段校验：拒绝 ..、反斜杠、控制字符与超长段，阻断路径穿越与非法 key 字符
     */
    private static void validateSegments(List<String> segments) {
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.contains("\\") || segment.length() > MAX_PATH_LENGTH
                    || segment.chars().anyMatch(c -> c < 0x20 || c == 0x7F)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "链接路径包含不合法字符");
            }
        }
        int totalLength = segments.stream().mapToInt(String::length).sum() + segments.size();
        if (totalLength > 1000) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "链接路径过长");
        }
    }
}
