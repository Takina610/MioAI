package com.mio.ai.framework.tools.CommonTools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 终端命令白名单纯单元测试（不依赖任何外部环境）
 */
class TerminalCommandPolicyTest {

    private final TerminalCommandPolicy policy = TerminalCommandPolicy.defaultPolicy();

    @Test
    void allowsWhitelistedCommands() {
        Assertions.assertNull(policy.checkAllowed("dir"));
        Assertions.assertNull(policy.checkAllowed("java -version"));
        Assertions.assertNull(policy.checkAllowed("python app.py"));
        Assertions.assertNull(policy.checkAllowed("npm install"));
        Assertions.assertNull(policy.checkAllowed("type readme.txt"));
    }

    @Test
    void rejectsNonWhitelistedCommands() {
        Assertions.assertNotNull(policy.checkAllowed("del readme.txt"));
        Assertions.assertNotNull(policy.checkAllowed("rm -rf /"));
        Assertions.assertNotNull(policy.checkAllowed("format d:"));
        Assertions.assertNotNull(policy.checkAllowed("reg add HKLM\\Software"));
        Assertions.assertNotNull(policy.checkAllowed("shutdown /s"));
        Assertions.assertNotNull(policy.checkAllowed("powershell -Command ..."));
        Assertions.assertNotNull(policy.checkAllowed("curl http://evil.com"));
    }

    @Test
    void rejectsCommandChaining() {
        // 白名单命令 + 链式拼接绕过，必须拒绝
        Assertions.assertNotNull(policy.checkAllowed("dir & del readme.txt"));
        Assertions.assertNotNull(policy.checkAllowed("echo hello | findstr x; rm -rf /"));
        Assertions.assertNotNull(policy.checkAllowed("type a.txt > system32.dll"));
        Assertions.assertNotNull(policy.checkAllowed("dir `del readme.txt`"));
        Assertions.assertNotNull(policy.checkAllowed("echo $(del readme.txt)"));
    }

    @Test
    void rejectsBlankCommand() {
        Assertions.assertNotNull(policy.checkAllowed(null));
        Assertions.assertNotNull(policy.checkAllowed("   "));
    }

    @Test
    void caseInsensitiveFirstToken() {
        Assertions.assertNull(policy.checkAllowed("DIR /w"));
        Assertions.assertNull(policy.checkAllowed("Python app.py"));
    }

    @Test
    void customConfigOverridesDefaults() {
        TerminalCommandPolicy custom = TerminalCommandPolicy.fromConfig("dir, type");
        Assertions.assertNull(custom.checkAllowed("dir"));
        Assertions.assertNotNull(custom.checkAllowed("python app.py"));
        Assertions.assertEquals(2, custom.getAllowedCommands().size());
    }
}
