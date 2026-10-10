package com.mio.ai.resource.service.mcp.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP 工具名前缀化（mcp__<server>__<tool>）的清洗规则：
 * OpenAI 兼容函数名只允许字母数字下划线连字符，总长 ≤64
 */
class McpToolNameSanitizeTest {

    @Test
    void normalNameKept() {
        assertEquals("deepwiki", McpClientManagerServiceImpl.sanitizeSegment("deepwiki"));
    }

    @Test
    void illegalCharsReplacedAndCollapsed() {
        assertEquals("amap_maps", McpClientManagerServiceImpl.sanitizeSegment("amap..maps"));
        assertEquals("a_b", McpClientManagerServiceImpl.sanitizeSegment("a___b"));
        assertEquals("a-b", McpClientManagerServiceImpl.sanitizeSegment("a-b"));
    }

    @Test
    void chineseNameFallsBack() {
        assertEquals("srv", McpClientManagerServiceImpl.sanitizeSegment("测试服务器"));
        assertEquals("srv", McpClientManagerServiceImpl.sanitizeSegment("工具"));
    }

    @Test
    void blankFallsBack() {
        assertEquals("srv", McpClientManagerServiceImpl.sanitizeSegment(""));
        assertEquals("srv", McpClientManagerServiceImpl.sanitizeSegment(null));
        assertEquals("srv", McpClientManagerServiceImpl.sanitizeSegment("///"));
    }

    @Test
    void truncatedTo64() {
        String longName = "mcp__" + "x".repeat(80) + "__tool";
        assertEquals(64, McpClientManagerServiceImpl.truncate(longName).length());
    }

    @Test
    void prefixedNameIsProviderSafe() {
        String name = McpClientManagerServiceImpl.truncate(
                "mcp__" + McpClientManagerServiceImpl.sanitizeSegment("DeepWiki.MCP")
                        + "__" + McpClientManagerServiceImpl.sanitizeSegment("ask question"));
        assertTrue(name.matches("[a-zA-Z0-9_-]{1,64}"), name);
        assertFalse(name.contains(" "), name);
    }
}
