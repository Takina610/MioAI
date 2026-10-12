package com.mio.ai.bot.service.image;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.micrometer.common.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Openverse 图片搜索（https://api.openverse.org，免 key，CC 授权图库索引）。
 * <p>匿名限流 20/min、200/day（响应头 x-ratelimit-*，实测 2026-10-12）；
 * 每张图携带真实 license/attribution 字段，照实透传，前端展示授权信息。
 */
class OpenverseProvider implements ImageSearchProvider {

    private static final int TIMEOUT_MS = 8_000;
    private static final String UA = "MioAI/1.0 (chat image illustration)";

    private final String baseUrl;

    OpenverseProvider(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public String name() {
        return "Openverse";
    }

    @Override
    public List<ImageResult> search(String query, int limit) {
        String url = baseUrl + (baseUrl.endsWith("?") ? "" : "?") + "q=" + urlEncode(query)
                + "&page_size=" + Math.min(Math.max(limit, 1), 20);
        String body = HttpRequest.get(url)
                .header("User-Agent", UA)
                .timeout(TIMEOUT_MS)
                .execute()
                .body();
        JSONObject json = JSONUtil.parseObj(body);
        JSONArray results = json.getJSONArray("results");
        List<ImageResult> out = new ArrayList<>();
        if (results == null) {
            return out;
        }
        for (Object item : results) {
            JSONObject obj = (JSONObject) item;
            if (obj.getBool("mature", false)) {
                continue;
            }
            String imageUrl = httpsOnly(obj.getStr("url"));
            if (imageUrl == null) {
                continue;
            }
            String license = obj.getStr("license");
            String licenseVersion = obj.getStr("license_version");
            String licenseText = license == null ? null
                    : "CC " + license.toUpperCase(Locale.ROOT)
                    + (licenseVersion == null ? "" : " " + licenseVersion);
            out.add(new ImageResult(
                    "ov:" + obj.getStr("id"),
                    imageUrl,
                    httpsOnly(obj.getStr("thumbnail")),
                    obj.getStr("foreign_landing_url"),
                    StringUtils.isNotBlank(obj.getStr("provider")) ? obj.getStr("provider") : "Openverse",
                    obj.getStr("title"),
                    null,
                    obj.getStr("creator"),
                    licenseText,
                    positiveInt(obj.getStr("width")),
                    positiveInt(obj.getStr("height"))));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 仅放行 https（协议白名单：http/ftp/data 等一律拒收） */
    private static String httpsOnly(String url) {
        return url != null && url.startsWith("https://") ? url : null;
    }

    private static Integer positiveInt(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
