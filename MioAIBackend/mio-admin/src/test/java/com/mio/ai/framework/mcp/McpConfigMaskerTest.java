package com.mio.ai.framework.mcp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * MCP 配置脱敏纯单元测试（不依赖任何外部环境）
 */
class McpConfigMaskerTest {

    @Test
    void masksHeadersAndEnvValues() {
        String config = """
                {"mcpServers":{"github":{"type":"http","url":"https://api.example.com/mcp",
                "headers":{"Authorization":"Bearer secret-token","X-Api-Key":"abc"},
                "env":{"GITHUB_TOKEN":"ghp_secret","DEBUG":"true"}}}}
                """;
        String masked = McpConfigMasker.mask(config);
        Assertions.assertFalse(masked.contains("secret-token"), masked);
        Assertions.assertFalse(masked.contains("ghp_secret"), masked);
        Assertions.assertFalse(masked.contains("abc"), masked);
        // 结构与主机保留：用户仍能看懂配置形态
        Assertions.assertTrue(masked.contains("https://api.example.com/mcp"), masked);
        Assertions.assertTrue(masked.contains("***"), masked);
    }

    @Test
    void masksQueryValuesButKeepsHostAndPath() {
        String masked = McpConfigMasker.mask("""
                {"mcpServers":{"s":{"url":"https://h.example.com/sse?token=abc123&lang=zh"}}}
                """);
        Assertions.assertFalse(masked.contains("abc123"), masked);
        Assertions.assertFalse(masked.contains("zh"), masked);
        Assertions.assertTrue(masked.contains("https://h.example.com/sse?"), masked);
    }

    @Test
    void masksSensitiveArgsButKeepsOthers() {
        String masked = McpConfigMasker.mask("""
                {"mcpServers":{"s":{"command":"npx","args":["-y","server","--api-key=sk-live-1","--port=8080"]}}}
                """);
        Assertions.assertFalse(masked.contains("sk-live-1"), masked);
        Assertions.assertTrue(masked.contains("--port=8080"), masked);
        Assertions.assertTrue(masked.contains("npx"), masked);
    }

    @Test
    void malformedOrEmptyConfigIsReturnedUnchanged() {
        Assertions.assertNull(McpConfigMasker.mask(null));
        Assertions.assertEquals("  ", McpConfigMasker.mask("  "));
        Assertions.assertEquals("{not json", McpConfigMasker.mask("{not json"));
    }

    @Test
    void detectsAlreadyMaskedConfigSoItIsNotSavedBack() {
        String raw = """
                {"mcpServers":{"s":{"url":"https://h.example.com/sse?token=abc123",
                "headers":{"Authorization":"Bearer real-token"},"env":{"K":"v"}}}}
                """;
        Assertions.assertFalse(McpConfigMasker.isMasked(raw), "真实配置不应被判为脱敏态");

        String masked = McpConfigMasker.mask(raw);
        Assertions.assertTrue(McpConfigMasker.isMasked(masked), "脱敏后的配置必须能被识别");
        // 掩码后的配置再脱敏应保持幂等（不出现 *** 嵌套）
        Assertions.assertEquals(masked, McpConfigMasker.mask(masked));
    }

    @Test
    void configWithoutSensitiveFieldsIsNotTreatedAsMasked() {
        String plain = """
                {"mcpServers":{"s":{"url":"https://h.example.com/mcp","args":["-y","server"]}}}
                """;
        Assertions.assertFalse(McpConfigMasker.isMasked(plain));
        Assertions.assertFalse(McpConfigMasker.isMasked(null));
        Assertions.assertFalse(McpConfigMasker.isMasked("{not json"));
    }
}
