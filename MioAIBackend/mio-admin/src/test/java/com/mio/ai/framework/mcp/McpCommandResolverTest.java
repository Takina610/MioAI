package com.mio.ai.framework.mcp;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Windows 命令解析逻辑测试：行为断言仅在 Windows 下生效，其他平台自动跳过
 */
class McpCommandResolverTest {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    @TempDir
    Path tempDir;

    @Test
    void npxResolvesToCmdNotBareShScript() throws Exception {
        Assumptions.assumeTrue(WINDOWS);
        // node 目录同时有无扩展名 sh 脚本和 .cmd，必须选 .cmd
        Files.createFile(tempDir.resolve("npx"));
        Files.createFile(tempDir.resolve("npx.cmd"));
        String resolved = McpCommandResolver.resolve("npx", tempDir.toString());
        assertTrue(resolved.endsWith("npx.cmd"), resolved);
    }

    @Test
    void exeSuffixResolved() throws Exception {
        Assumptions.assumeTrue(WINDOWS);
        Files.createFile(tempDir.resolve("node.exe"));
        assertEquals(tempDir.resolve("node.exe").toAbsolutePath().toString(),
                McpCommandResolver.resolve("node", tempDir.toString()));
    }

    @Test
    void pathLikeCommandResolvedWithSuffix() throws Exception {
        Assumptions.assumeTrue(WINDOWS);
        Path server = tempDir.resolve("server.cmd");
        Files.createFile(server);
        String command = tempDir.resolve("server").toString();
        assertEquals(server.toAbsolutePath().toString(), McpCommandResolver.resolve(command, ""));
    }

    @Test
    void alreadyHasExtensionUnchanged() {
        assertEquals("npx.cmd", McpCommandResolver.resolve("npx.cmd", ""));
        assertEquals("C:\\tools\\mcp.exe", McpCommandResolver.resolve("C:\\tools\\mcp.exe", ""));
    }

    @Test
    void notFoundReturnsOriginal() {
        Assumptions.assumeTrue(WINDOWS);
        assertEquals("no-such-cmd-xyz", McpCommandResolver.resolve("no-such-cmd-xyz", tempDir.toString()));
    }

    @Test
    void nullAndBlankPassthrough() {
        assertNull(McpCommandResolver.resolve(null));
        assertEquals("", McpCommandResolver.resolve(""));
    }
}
