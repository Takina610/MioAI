package com.mio.ai.bot.service.image;

import java.util.List;

/**
 * 图片来源适配器：把单一图片平台适配成统一的候选列表，
 * 聊天业务只依赖本接口（平台鉴权/限流/字段差异全部收敛在实现内）。
 */
public interface ImageSearchProvider {

    /** 来源名（前端来源信息展示用，如 Openverse / AniList） */
    String name();

    /**
     * 检索图片候选。实现负责：超时与异常（抛出由服务层统一降级）、
     * 结果规范化（仅 https、剔除成熟内容）、授权/作者字段照实透传。
     */
    List<ImageResult> search(String query, int limit);
}
