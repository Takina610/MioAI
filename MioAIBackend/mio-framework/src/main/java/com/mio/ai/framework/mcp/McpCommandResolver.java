package com.mio.ai.framework.mcp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Windows 下 STDIO 命令解析：CreateProcess 只对 .exe 做无扩展名 PATH 搜索，
 * npx/npm/pnpm 等实为 .cmd 脚本，配置写 "command": "npx" 会直接启动失败（Linux 则原样可用）。
 * 这里按 .cmd/.exe/.bat 顺序在 PATH（或命令自身路径）中解析出真实可执行文件；
 * 非 Windows 或解析不到时原样返回，交由子进程报真实错误。
 */
final class McpCommandResolver {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    /**
     * .cmd 优先、裸名垫底：node 安装目录同时存在无扩展名的 sh 脚本（npx）与 npx.cmd，
     * 只有后者能被 Windows 启动，裸名排最后避免误选 sh 脚本
     */
    private static final List<String> EXE_SUFFIXES = List.of(".cmd", ".exe", ".bat", "");

    private McpCommandResolver() {
    }

    static String resolve(String command) {
        return resolve(command, System.getenv("PATH"));
    }

    /**
     * pathEnv 可注入便于测试；null/空时取当前进程 PATH
     */
    static String resolve(String command, String pathEnv) {
        if (!WINDOWS || command == null || command.isBlank()) {
            return command;
        }
        String trimmed = command.trim();
        String lower = trimmed.toLowerCase();
        if (lower.endsWith(".exe") || lower.endsWith(".cmd") || lower.endsWith(".bat") || lower.endsWith(".com")) {
            return trimmed;
        }
        if (trimmed.contains("\\") || trimmed.contains("/")) {
            return resolvePathLike(trimmed);
        }
        return resolveInPath(trimmed, pathEnv != null && !pathEnv.isBlank() ? pathEnv : System.getenv("PATH"));
    }

    private static String resolvePathLike(String command) {
        for (String suffix : EXE_SUFFIXES) {
            Path candidate = Path.of(command + suffix);
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
        }
        return command;
    }

    private static String resolveInPath(String command, String pathEnv) {
        if (pathEnv == null || pathEnv.isBlank()) {
            return command;
        }
        for (String dir : pathEnv.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            for (String suffix : EXE_SUFFIXES) {
                Path candidate = Path.of(dir, command + suffix);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().toString();
                }
            }
        }
        return command;
    }
}
