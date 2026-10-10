package com.mio.ai.framework.skillssh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: skills.sh 公共目录搜索客户端（对齐 cc-switch）。
 *               GET https://skills.sh/api/search?q=&limit=&offset=，
 *               返回 {query, searchType, skills: [{id, skillId, name, installs, source}], count}，
 *               source 即 "owner/repo"。只读接口，不带任何凭证。
 */
@Slf4j
@Component
public class SkillsShClient {

    private static final String SEARCH_URL = "https://skills.sh/api/search";
    private static final String USER_AGENT = "MioAI-Skill-Search";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** skills.sh 单条结果 */
    public record SkillsShEntry(String skillId, String name, long installs, String source) {
    }

    public record SkillsShSearchResult(List<SkillsShEntry> skills, long total) {
    }

    public SkillsShSearchResult search(String query, int limit, int offset) {
        String url = SEARCH_URL + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&limit=" + limit + "&offset=" + offset;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "skills.sh 搜索失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "skills.sh 搜索被中断");
        }
        if (response.statusCode() != 200) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "skills.sh 搜索失败: HTTP " + response.statusCode());
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(response.body());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "解析 skills.sh 响应失败");
        }
        List<SkillsShEntry> entries = new ArrayList<>();
        for (JsonNode node : root.path("skills")) {
            String skillId = node.path("skillId").asText("");
            String name = node.path("name").asText("");
            String source = node.path("source").asText("");
            if (skillId.isEmpty() || source.isEmpty()) {
                continue;
            }
            entries.add(new SkillsShEntry(skillId, name, node.path("installs").asLong(0), source));
        }
        return new SkillsShSearchResult(entries, root.path("count").asLong(entries.size()));
    }
}
