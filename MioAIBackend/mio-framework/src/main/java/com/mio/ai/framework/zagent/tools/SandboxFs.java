package com.mio.ai.framework.zagent.tools;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.framework.sandbox.SandboxSession;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 沙箱文件系统（zcode fileSystemPort 的对位移植）：
 * Read/Write/Edit/Glob/Grep/Bash 全部落到真实 Linux 沙箱。
 * <p>路径约定与 {@link SandboxSession} 一致：工具看到的路径为工作目录内相对路径，
 * 容忍 ~/workdir、./ 前缀等模型常见写法。
 */
public final class SandboxFs {

    private final SandboxSession session;
    private final String workdirName;

    public SandboxFs(SandboxSession session, String workdirName) {
        this.session = session;
        this.workdirName = StrUtil.blankToDefault(workdirName, "sandbox");
    }

    public record FileStat(boolean exists, boolean directory, long mtimeSec, long size) {
    }

    public record ExecResult(String output, Integer exitCode, boolean timedOut) {
    }

    public String workdirDisplay() {
        return "~/" + workdirName;
    }

    /** 解析为工作目录内相对路径；非法路径抛 IllegalArgumentException（消息可直接给模型） */
    public String resolve(String path) {
        return session.resolveRelative(path);
    }

    public ExecResult exec(String command, Integer timeoutSeconds, String cwdRelative) {
        SandboxSession.ExecResult result = session.execRaw(command, timeoutSeconds, cwdRelative);
        return new ExecResult(result.output(), result.exitCode(), result.timedOut());
    }

    public FileStat stat(String relPath) {
        SandboxSession.ExecResult result = session.exec(
                "stat -c '%F|%Y|%s' " + quote(relPath) + " 2>/dev/null", null);
        if (result.exitCode() == null || result.exitCode() != 0 || StrUtil.isBlank(result.output())) {
            return new FileStat(false, false, 0, 0);
        }
        String[] parts = result.output().trim().split("\\|");
        if (parts.length < 3) {
            return new FileStat(false, false, 0, 0);
        }
        try {
            return new FileStat(true, parts[0].startsWith("directory"),
                    Long.parseLong(parts[1]), Long.parseLong(parts[2]));
        } catch (NumberFormatException e) {
            return new FileStat(true, false, 0, 0);
        }
    }

    /** 读全文（UTF-8）；文件不存在时抛异常 */
    public String readAll(String relPath) {
        return session.readFile(relPath);
    }

    /** 读原始字节（SFTP 二进制安全，图片读取用）；超 maxBytes 抛 IllegalStateException */
    public byte[] readFileBytes(String relPath, long maxBytes) {
        String safe;
        try {
            safe = session.resolveRelative(relPath);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        try {
            return session.withSftp(sftp -> {
                cdWorkdir(sftp);
                try (java.io.InputStream in = sftp.get(safe);
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        total += n;
                        if (total > maxBytes) {
                            throw new IllegalStateException(
                                    "File exceeds maximum readable size of " + maxBytes + " bytes");
                        }
                        out.write(buffer, 0, n);
                    }
                    return out.toByteArray();
                }
            });
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取失败: " + relPath + "（" + e.getMessage() + "）", e);
        }
    }

    /** SFTP 通道先 cd 到工作目录，之后的相对路径都落在工作区内（对齐 SandboxFileTransfer） */
    private void cdWorkdir(com.jcraft.jsch.ChannelSftp sftp) throws Exception {
        String workdir = session.workdirRelative();
        if (StrUtil.isBlank(workdir)) {
            return;
        }
        sftp.cd(workdir);
    }

    /** 行切片 [startLine, endLine]（1 闭区间），返回实际行数组 */
    public List<String> readLines(String relPath, int startLine, int endLine) {
        SandboxSession.ExecResult result = session.exec(
                "sed -n '" + startLine + "," + endLine + "p' " + quote(relPath), null);
        if (result.exitCode() == null || result.exitCode() != 0) {
            throw new IllegalStateException("Failed to read " + relPath);
        }
        return splitLines(result.output());
    }

    public int countLines(String relPath) {
        SandboxSession.ExecResult result = session.exec("wc -l < " + quote(relPath), null);
        if (result.exitCode() == null || result.exitCode() != 0) {
            return 0;
        }
        try {
            return Integer.parseInt(result.output().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void write(String relPath, String content) {
        session.writeFile(relPath, content);
    }

    /** 列目录名（供文件不存在时的相似名建议） */
    public List<String> listNames(String relDir) {
        SandboxSession.ExecResult result = session.exec(
                "ls -A " + quote(StrUtil.blankToDefault(relDir, ".")) + " 2>/dev/null", null);
        if (result.exitCode() == null || result.exitCode() != 0) {
            return List.of();
        }
        return splitLines(result.output());
    }

    /** glob 匹配（mtime 降序，上限 maxResults+1 以识别截断） */
    public List<String> glob(String root, String pattern, int maxResults) {
        String searchRoot = StrUtil.blankToDefault(root, ".");
        String findExpr;
        if (!pattern.contains("/")) {
            findExpr = "find " + quote(searchRoot) + " -maxdepth 1 -type f -name " + quote(pattern);
        } else {
            // ** 与 * 在 find -path 下等价（* 可跨越 /）；锚定到搜索根
            String translated = pattern.replace("**/", "*");
            findExpr = "find " + quote(searchRoot) + " -type f -path " + quote(searchRoot + "/" + translated);
        }
        SandboxSession.ExecResult result = session.exec(
                findExpr + " -printf '%T@ %p\\n' 2>/dev/null | sort -rn | head -n " + (maxResults + 1)
                        + " | cut -d' ' -f2-", null);
        if (result.exitCode() == null || result.exitCode() != 0) {
            return List.of();
        }
        List<String> lines = splitLines(result.output());
        List<String> cleaned = new ArrayList<>();
        for (String line : lines) {
            String rel = stripRoot(line.trim());
            if (!rel.isEmpty()) {
                cleaned.add(rel);
            }
        }
        return cleaned;
    }

    /** 内容搜索：grep -rE 变体，返回原始输出行（path:line:text 等） */
    public ExecResult grep(String flags, String includeGlob, String pattern, String path) {
        StringBuilder command = new StringBuilder("grep -rE ");
        command.append(flags);
        if (includeGlob != null && !includeGlob.isBlank()) {
            command.append(" --include=").append(quote(includeGlob));
        }
        command.append(" -- ").append(quote(pattern)).append(" ")
                .append(quote(StrUtil.blankToDefault(path, ".")));
        return exec(command.toString(), null, null);
    }

    public String stripRoot(String path) {
        String workdir = workdirName;
        int idx = path.indexOf("/" + workdir + "/");
        if (idx >= 0) {
            return path.substring(idx + workdir.length() + 2);
        }
        if (path.startsWith("./")) {
            return path.substring(2);
        }
        return path;
    }

    public static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        for (String line : text.split("\r?\n", -1)) {
            lines.add(StrUtil.removeSuffix(line, "\r"));
        }
        // sed/wc 输出尾部带一个换行产生的空行
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
