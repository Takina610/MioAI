package com.mio.ai.framework.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub REST API / raw 内容客户端。
 *               安全约束：仅访问主机白名单内的地址；重定向逐跳复检白名单（防 SSRF）；
 *               响应体按上限截断读取（防大响应打爆内存）；token 走 mio.ai.github.token 配置，只发给 api.github.com。
 */
@Slf4j
@Component
public class GithubClient {

    /** raw 的 LFS 媒体重定向会落到 objects.githubusercontent.com，一并放行 */
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "api.github.com",
            "raw.githubusercontent.com",
            "objects.githubusercontent.com");

    private static final String API_BASE = "https://api.github.com";
    private static final String RAW_BASE = "https://raw.githubusercontent.com";
    private static final String USER_AGENT = "MioAI-KB-Import";
    private static final int MAX_REDIRECTS = 3;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${mio.ai.github.token:}")
    private String token;

    public record GithubRepoInfo(String defaultBranch, long sizeKb) {
    }

    public record GithubTreeEntry(String path, String type, long size) {
    }

    public record GithubTree(boolean truncated, List<GithubTreeEntry> entries) {
    }

    /**
     * 仓库元信息（默认分支、体积 KB）
     */
    public GithubRepoInfo fetchRepoInfo(String owner, String repo) {
        JsonNode root = apiGetJson(API_BASE + "/repos/" + encodeSegment(owner) + "/" + encodeSegment(repo));
        return new GithubRepoInfo(root.path("default_branch").asText("main"), root.path("size").asLong(0));
    }

    /**
     * 完整文件树（recursive=1）。仓库条目超上限时 GitHub 返回 truncated，调用方需据此拒绝导入
     */
    public GithubTree fetchTree(String owner, String repo, String ref) {
        String url = API_BASE + "/repos/" + encodeSegment(owner) + "/" + encodeSegment(repo)
                + "/git/trees/" + encodeSegment(ref) + "?recursive=1";
        JsonNode root = apiGetJson(url);
        List<GithubTreeEntry> entries = new ArrayList<>();
        for (JsonNode node : root.path("tree")) {
            entries.add(new GithubTreeEntry(
                    node.path("path").asText(),
                    node.path("type").asText(),
                    node.path("size").asLong(0)));
        }
        return new GithubTree(root.path("truncated").asBoolean(false), entries);
    }

    /**
     * 拉取 raw 文件内容，超过 maxBytes 视为失败
     */
    public byte[] fetchRawFile(String owner, String repo, String ref, String path, long maxBytes) {
        String url = RAW_BASE + "/" + encodeSegment(owner) + "/" + encodeSegment(repo)
                + "/" + encodeSegment(ref) + "/" + encodePath(path);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<InputStream> response;
            try {
                response = HTTP.send(request, BodyHandlers.ofInputStream());
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "下载 GitHub 文件失败: " + e.getMessage());
            }

            int status = response.statusCode();
            if (status >= 301 && status <= 308) {
                closeQuietly(response.body());
                url = resolveRedirect(url, response.headers().firstValue("location").orElse(null));
                continue;
            }
            if (status == 404) {
                closeQuietly(response.body());
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在或为空仓库: " + path);
            }
            if (status != 200) {
                closeQuietly(response.body());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "下载 GitHub 文件失败: HTTP " + status);
            }
            return readCapped(response.body(), path, maxBytes);
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "重定向次数过多: " + path);
    }

    /**
     * 重定向目标必须仍在白名单主机内，防止被引到内网或任意站点
     */
    private String resolveRedirect(String currentUrl, String location) {
        if (location == null || location.isBlank()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "GitHub 返回的重定向缺少目标地址");
        }
        URI target = URI.create(currentUrl).resolve(location);
        String host = target.getHost();
        if (host == null || !ALLOWED_HOSTS.contains(host.toLowerCase())
                || !"https".equalsIgnoreCase(target.getScheme())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "下载地址被重定向到非 GitHub 主机，已拦截");
        }
        return target.toString();
    }

    private static void closeQuietly(InputStream body) {
        try {
            body.close();
        } catch (IOException ignored) {
            // 丢弃错误响应体时的关闭异常无需处理
        }
    }

    /**
     * 按 Content-Length 与实际上限双保险截断读取
     */
    private byte[] readCapped(InputStream body, String path, long maxBytes) {
        try (body) {
            int limit = (int) Math.min(maxBytes, Integer.MAX_VALUE - 8);
            byte[] buffer = body.readNBytes(limit + 1);
            if (buffer.length > limit) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR,
                        "文件超过大小上限，已跳过: " + path);
            }
            return buffer;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取 GitHub 文件失败: " + path);
        }
    }

    private JsonNode apiGetJson(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET();
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }

        HttpResponse<String> response;
        try {
            response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请求 GitHub 接口失败: " + e.getMessage());
        }

        int status = response.statusCode();
        if (status == 401) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "GitHub token 无效，请检查配置");
        }
        if (status == 403 && "0".equals(response.headers().firstValue("x-ratelimit-remaining").orElse("1"))) {
            log.warn("GitHub API 限流: url={}", url);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "GitHub 接口调用次数已达上限，请稍后再试或配置 GITHUB_TOKEN 提升额度");
        }
        if (status == 404) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "GitHub 资源不存在，请检查链接与分支是否正确");
        }
        if (status != 200) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请求 GitHub 接口失败: HTTP " + status);
        }
        try {
            return MAPPER.readTree(response.body());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "解析 GitHub 响应失败");
        }
    }

    /** 单段编码（owner/repo/ref/路径段），斜杠按段处理，空格转 %20 而非 + */
    private String encodeSegment(String segment) {
        return java.net.URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 整条路径按 / 拆段后逐段编码再拼回，保持目录分隔符 */
    private String encodePath(String path) {
        StringBuilder sb = new StringBuilder();
        for (String segment : path.split("/")) {
            if (!segment.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('/');
                }
                sb.append(encodeSegment(segment));
            }
        }
        return sb.toString();
    }
}
