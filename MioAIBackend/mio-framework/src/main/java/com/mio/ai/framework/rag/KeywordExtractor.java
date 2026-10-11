package com.mio.ai.framework.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author: Takina
 * @date: 2026/10/11
 * @description: 查询关键词提取：英文取连续字母/数字词，中文取二元组（bigram）。
 *               pgvector 侧没有中文分词器，二元组子串匹配是在纯 PG 上做关键词召回
 *               （专有名词/精确术语）最务实的方式，配合向量召回做 RRF 融合。
 */
public final class KeywordExtractor {

    /**
     * 单次检索最多参与匹配的关键词数，防止 OR 链过长
     */
    private static final int MAX_TERMS = 12;

    /**
     * 高频口语二元组，对召回没有区分度
     */
    private static final Set<String> STOP_BIGRAMS = Set.of(
            "什么", "怎么", "如何", "哪个", "哪些", "为什么", "请问", "一下",
            "关于", "这个", "那个", "是否", "可以", "或者", "还是", "以及",
            "介绍", "说明", "告诉", "帮我", "我想", "我们", "他们", "自己");

    private KeywordExtractor() {
    }

    /**
     * @return 去重后的关键词列表（顺序保留原查询中的出现顺序），无可用词时为空
     */
    public static List<String> extract(String query) {
        return extract(query, MAX_TERMS);
    }

    public static List<String> extract(String query, int maxTerms) {
        List<String> terms = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return terms;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        Set<String> seen = new LinkedHashSet<>();

        int i = 0;
        int length = normalized.length();
        while (i < length && seen.size() < maxTerms) {
            char c = normalized.charAt(i);
            if (isLatin(c)) {
                int start = i;
                while (i < length && isLatin(normalized.charAt(i))) {
                    i++;
                }
                String word = normalized.substring(start, i);
                // 长度>=2 的词才有区分度（单字母多为误切分）
                if (word.length() >= 2) {
                    seen.add(word);
                }
            } else if (isCjk(c)) {
                int start = i;
                while (i < length && isCjk(normalized.charAt(i))) {
                    i++;
                }
                addCjkGrams(normalized.substring(start, i), seen);
            } else {
                i++;
            }
        }
        terms.addAll(seen);
        return terms;
    }

    /**
     * 中文连续段拆成二元组；单字成段（如"卡"）时保留单字
     */
    private static void addCjkGrams(String run, Set<String> seen) {
        if (run.length() == 1) {
            seen.add(run);
            return;
        }
        for (int j = 0; j + 2 <= run.length() && seen.size() < MAX_TERMS; j++) {
            String gram = run.substring(j, j + 2);
            if (!STOP_BIGRAMS.contains(gram)) {
                seen.add(gram);
            }
        }
    }

    private static boolean isLatin(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private static boolean isCjk(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }
}
