package com.mio.ai.resource.service.mcp.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * MCP 配置脱敏可见性纯单元测试：回归锁定
 * ① 管理端审阅仍能拿到原始配置（不能被脱敏改坏）；
 * ② 广场/他人查询一律脱敏；
 * ③ 所有者本人不脱敏（编辑页回填依赖它）。
 */
class McpSecretVisibilityTest {

    private static final long OWNER = 7L;
    private static final long OTHER_USER = 8L;

    @Test
    void adminReviewAlwaysGetsRawConfig() {
        // 管理端查看他人创建的 MCP：必须拿到原始配置
        Assertions.assertFalse(McpToolServiceImpl.shouldMaskSecrets(OTHER_USER, OWNER, true));
        // 甚至无法解析出管理员 id 时也不脱敏（管理端入口已由 @AuthCheck 把关）
        Assertions.assertFalse(McpToolServiceImpl.shouldMaskSecrets(null, OWNER, true));
    }

    @Test
    void businessCallersAreMaskedUnlessOwner() {
        // 所有者本人：不脱敏（编辑/详情需回填）
        Assertions.assertFalse(McpToolServiceImpl.shouldMaskSecrets(OWNER, OWNER, false));
        // 其他登录用户（含公开 MCP 的查看者）：脱敏
        Assertions.assertTrue(McpToolServiceImpl.shouldMaskSecrets(OTHER_USER, OWNER, false));
        // 匿名（广场）：脱敏
        Assertions.assertTrue(McpToolServiceImpl.shouldMaskSecrets(null, OWNER, false));
    }

    @Test
    void ownerLookupWithNullOwnerIdIsMasked() {
        // 无主 MCP：任何业务侧查看者都应脱敏
        Assertions.assertTrue(McpToolServiceImpl.shouldMaskSecrets(OWNER, null, false));
    }
}
