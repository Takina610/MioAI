package com.mio.ai.bot.service.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片搜索服务：Provider 路由 + 结果登记。
 * <p>general 走 Openverse（零结果时回退 AniList），anime 走 AniList；
 * 每次真实返回的 URL 进 {@link ImageUrlRegistry}（render_ui 图片块的防伪造核对来源）。
 * 开关 image.search.enabled=false 时返回空列表（聊天照常，仅无图）。
 */
@Slf4j
@Service
public class ImageSearchService {

    private final ImageUrlRegistry registry;
    private final ImageSearchProvider openverse;
    private final ImageSearchProvider anilist;
    private final boolean enabled;

    public ImageSearchService(ImageUrlRegistry registry,
                              @Value("${image.search.enabled:true}") boolean enabled,
                              @Value("${image.search.openverse-url:https://api.openverse.org/v1/images/}")
                              String openverseUrl,
                              @Value("${image.search.anilist-url:https://graphql.anilist.co}")
                              String anilistUrl) {
        this.registry = registry;
        this.enabled = enabled;
        this.openverse = new OpenverseProvider(openverseUrl);
        this.anilist = new AniListProvider(anilistUrl);
    }

    /**
     * @param category general=通用（Openverse，零结果回退动漫库）；anime=动漫角色/作品（AniList）
     */
    public List<ImageResult> search(String chatId, String query, String category, int limit) {
        if (!enabled) {
            return List.of();
        }
        List<ImageResult> results = new ArrayList<>();
        try {
            if ("anime".equals(category)) {
                results.addAll(anilist.search(query, limit));
            } else {
                results.addAll(openverse.search(query, limit));
                if (results.isEmpty()) {
                    results.addAll(anilist.search(query, limit));
                }
            }
        } catch (Exception e) {
            log.warn("图片搜索失败, provider={}, query={}: {}", "anime".equals(category) ? "anilist" : "openverse",
                    query, e.getMessage());
            throw new IllegalStateException("image search unavailable: " + e.getMessage(), e);
        }
        if (!results.isEmpty()) {
            registry.register(chatId, results);
        }
        return results;
    }

    /**
     * render_ui 图片块校验用：url 是否为本会话 image_search 真实结果，
     * 命中返回完整元数据（供校验器回填来源/作者/授权），未命中返回 null。
     */
    public ImageResult resolveImage(String chatId, String url) {
        return registry.lookup(chatId, url);
    }
}
