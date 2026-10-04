package com.mio.ai.framework.sandbox;

import cn.hutool.core.util.StrUtil;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.mio.ai.framework.sandbox.SandboxProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * 沙箱会话：与 Linux 沙箱之间的一条可复用 SSH 长连接（单用户毕设场景，命令串行执行）。
 * <p>参考 pi 的 BashOperations 抽象与 codex 的 unified_exec：所有文件/命令工具
 * （bash、read、write、edit、glob、grep）都落到这台真实机器上，Agent 因此获得
 * 与本地编码 Agent 等价的能力面——不再需要任何领域定制工具。
 * <p>路径约定：文件类工具只允许访问工作目录内的相对路径（拒绝绝对路径与 ..）；
 * 命令自由执行（自毁式命令黑名单尽力拦截），输出统一尾部截断。
 */
@Slf4j
public class SandboxSession {

    /** 尽力拦截的自毁/破坏式命令片段（小写匹配） */
    private static final List<String> BLOCKED_FRAGMENTS = List.of(
            "mkfs", "shutdown", "reboot", "poweroff", "halt", "init 0", "init 6",
            ":(){", "fork bomb", "dd if=/dev/", ">/dev/sd", "rm -rf /", "rm -fr /");

    private static final int OUTPUT_HARD_CAP_CHARS = 16000;

    private final SandboxProperties props;
    private final Object lock = new Object();
    private Session session;

    public SandboxSession(SandboxProperties props) {
        this.props = props;
    }

    /** 单条命令执行结果 */
    public record ExecResult(String output, Integer exitCode, boolean timedOut) {
    }

    /**
     * 在工作目录执行命令，stdout+stderr 合并、尾部截断、附退出码
     */
    public ExecResult exec(String command, Integer timeoutSeconds) {
        String rejection = guard(command);
        if (rejection != null) {
            return new ExecResult(rejection, null, false);
        }
        int timeout = timeoutSeconds != null && timeoutSeconds > 0
                ? Math.min(timeoutSeconds, 600) : props.getExecTimeoutSeconds();
        synchronized (lock) {
            try {
                ensureSession();
                return runChannel("cd " + homeDir() + " && " + command, timeout);
            } catch (Exception e) {
                // 连接坏了丢弃，下次重连重试一次
                closeQuietly();
                try {
                    ensureSession();
                    return runChannel("cd " + homeDir() + " && " + command, timeout);
                } catch (Exception retry) {
                    log.warn("沙箱命令执行失败: {}", retry.getMessage());
                    return new ExecResult("沙箱执行出错: " + retry.getMessage(), null, false);
                }
            }
        }
    }

    /**
     * 原样执行（zagent 引擎用）：不加退出码/超时修饰、不做尾部截断，工作目录内可指定子目录。
     * 输出格式化（Exit code 前缀等）由调用方按 zcode Bash 工具语义自行完成。
     */
    public ExecResult execRaw(String command, Integer timeoutSeconds, String cwdRelative) {
        int timeout = timeoutSeconds != null && timeoutSeconds > 0
                ? Math.min(timeoutSeconds, 600) : props.getExecTimeoutSeconds();
        String cdChain = "cd " + homeDir();
        if (cwdRelative != null && !cwdRelative.isBlank() && !cwdRelative.contains("..")) {
            cdChain += " && cd " + quote(cwdRelative);
        }
        synchronized (lock) {
            try {
                ensureSession();
                return runChannelRaw(cdChain + " && " + command, timeout);
            } catch (Exception e) {
                closeQuietly();
                try {
                    ensureSession();
                    return runChannelRaw(cdChain + " && " + command, timeout);
                } catch (Exception retry) {
                    log.warn("沙箱命令执行失败: {}", retry.getMessage());
                    return new ExecResult("Sandbox execution error: " + retry.getMessage(), null, false);
                }
            }
        }
    }

    /** 工作目录内的相对路径 → 校验后的路径串（供 shell 引用）。
     * 容错规范化：模型常带 ~/sandbox/ 前缀或 ./ 前缀，直接剥掉而不是报错
     * （写入失败率最高的一类就是全路径被严格校验拒绝）。 */
    public String resolveRelative(String path) {
        if (StrUtil.isBlank(path)) {
            throw new IllegalArgumentException("路径不能为空");
        }
        String p = path.trim().replace("\\", "/");
        String workdir = StrUtil.blankToDefault(props.getWorkdir(), "sandbox")
                .replaceFirst("^~/?", "").replaceAll("^/+", "").replaceAll("/+$", "");
        p = p.replaceFirst("^~/?", "");
        if (!workdir.isEmpty()) {
            p = p.replaceFirst("^" + java.util.regex.Pattern.quote(workdir) + "/?", "");
            // 绝对路径含工作目录（/home/xx/sandbox/...）：取工作目录之后的部分
            int idx = p.indexOf("/" + workdir + "/");
            if (idx >= 0) {
                p = p.substring(idx + workdir.length() + 2);
            }
        }
        p = p.replaceFirst("^\\./", "").replaceAll("^/+", "");
        if (p.isEmpty() || p.contains("..")) {
            throw new IllegalArgumentException("路径必须是沙箱工作目录内的相对路径: " + path);
        }
        return p;
    }

    /** 读工作目录内文件全文（UTF-8） */
    public String readFile(String path) {
        String safe = resolveRelative(path);
        ExecResult result = exec("cat " + quote(safe), null);
        if (result.exitCode() != null && result.exitCode() != 0) {
            throw new IllegalArgumentException("读取失败（exit " + result.exitCode() + "）: " + safe
                    + "，可先用 glob 确认文件存在");
        }
        return result.output();
    }

    /** 写工作目录内文件（base64 传输规避引号转义），自动创建父目录 */
    public void writeFile(String path, String content) {
        String safe = resolveRelative(path);
        String encoded = Base64.getEncoder()
                .encodeToString((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        String dir = safe.contains("/") ? safe.substring(0, safe.lastIndexOf('/')) : ".";
        String command = "mkdir -p " + quote(dir) + " && printf %s '" + encoded + "' | base64 -d > " + quote(safe)
                + " && wc -c < " + quote(safe);
        ExecResult result = exec(command, null);
        if (result.exitCode() == null || result.exitCode() != 0) {
            throw new IllegalStateException("写入失败: " + result.output());
        }
    }

    public String workdirDisplay() {
        return "~/" + StrUtil.blankToDefault(props.getWorkdir(), "sandbox").replaceFirst("^~/?", "");
    }

    // ---------- 连接管理 ----------

    private void ensureSession() throws Exception {
        if (session != null && session.isConnected()) {
            return;
        }
        JSch jsch = new JSch();
        configureAuth(jsch);
        Session created = jsch.getSession(props.getUser(), props.getHost(), props.getPort());
        created.setConfig("StrictHostKeyChecking", "no");
        created.setServerAliveInterval(15000);
        created.connect(props.getConnectTimeoutMs());
        this.session = created;
    }

    private void configureAuth(JSch jsch) throws Exception {
        String keyPath = props.getPrivateKeyPath();
        if (StrUtil.isNotBlank(keyPath)) {
            if (!Files.exists(Path.of(keyPath))) {
                throw new IllegalStateException("沙箱私钥不存在: " + keyPath);
            }
            if (StrUtil.isNotBlank(props.getPassphrase())) {
                jsch.addIdentity(keyPath, props.getPassphrase());
            } else {
                jsch.addIdentity(keyPath);
            }
            return;
        }
        Path defaultKey = Path.of(System.getProperty("user.home"), ".ssh", "id_rsa");
        if (Files.exists(defaultKey)) {
            jsch.addIdentity(defaultKey.toString());
            return;
        }
        throw new IllegalStateException("未配置沙箱私钥（mio.ai.sandbox.private-key-path）");
    }

    /** 执行并采集输出：先拿输入流再 connect（jsch 大输出防死锁），超时强断 */
    private ExecResult runChannel(String command, int timeoutSeconds) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        channel.setOutputStream(out);
        channel.setErrStream(out);
        InputStream in = channel.getInputStream();
        channel.connect(props.getConnectTimeoutMs());
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        boolean timedOut = false;
        byte[] buffer = new byte[4096];
        while (!channel.isClosed()) {
            if (System.currentTimeMillis() > deadline) {
                timedOut = true;
                break;
            }
            drain(in, out, buffer);
            Thread.sleep(25);
        }
        drain(in, out, buffer);
        Integer exitCode = channel.isClosed() ? channel.getExitStatus() : null;
        channel.disconnect();

        String output = out.toString(StandardCharsets.UTF_8);
        if (output.length() > props.getMaxOutputChars()) {
            output = "[输出过长，仅保留末尾 " + props.getMaxOutputChars() + " 字符（共 " + output.length()
                    + " 字符）]\n" + output.substring(output.length() - props.getMaxOutputChars());
        }
        StringBuilder sb = new StringBuilder(output);
        if (timedOut) {
            sb.append("\n[命令超过 ").append(timeoutSeconds).append(" 秒被强制终止]");
        } else if (exitCode != null && exitCode != 0) {
            sb.append("\n[退出码: ").append(exitCode).append("]");
        }
        return new ExecResult(sb.toString(), exitCode, timedOut);
    }

    /** 原样执行并采集：输出不附加任何修饰（超限时强断并置 timedOut） */
    private ExecResult runChannelRaw(String command, int timeoutSeconds) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        channel.setOutputStream(out);
        channel.setErrStream(out);
        InputStream in = channel.getInputStream();
        channel.connect(props.getConnectTimeoutMs());
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        boolean timedOut = false;
        byte[] buffer = new byte[4096];
        while (!channel.isClosed()) {
            if (System.currentTimeMillis() > deadline) {
                timedOut = true;
                break;
            }
            drain(in, out, buffer);
            Thread.sleep(25);
        }
        drain(in, out, buffer);
        Integer exitCode = channel.isClosed() ? channel.getExitStatus() : null;
        channel.disconnect();
        return new ExecResult(out.toString(StandardCharsets.UTF_8), exitCode, timedOut);
    }

    /** 输出内存上限：超过硬上限后丢弃，只保末尾 */
    private void drain(InputStream in, ByteArrayOutputStream out, byte[] buffer) throws Exception {
        while (in.available() > 0) {
            int n = in.read(buffer);
            if (n < 0) {
                break;
            }
            if (out.size() < OUTPUT_HARD_CAP_CHARS * 2) {
                out.write(buffer, 0, n);
            }
        }
    }

    private String guard(String command) {
        if (StrUtil.isBlank(command)) {
            return "命令为空";
        }
        String normalized = command.toLowerCase();
        for (String fragment : BLOCKED_FRAGMENTS) {
            if (normalized.contains(fragment)) {
                return "命令被沙箱安全策略拒绝（包含破坏式操作: " + fragment + "），请换一种安全的做法";
            }
        }
        return null;
    }

    private String homeDir() {
        String dir = StrUtil.blankToDefault(props.getWorkdir(), "sandbox").trim();
        if (dir.startsWith("~/")) {
            dir = dir.substring(2);
        } else if (dir.equals("~")) {
            dir = "";
        }
        String target = dir.startsWith("/") ? dir : "$HOME/" + dir;
        return "\"" + target.replace("\"", "\\\"") + "\"";
    }

    private void closeQuietly() {
        if (session != null) {
            try {
                session.disconnect();
            } catch (Exception ignored) {
                // 连接本已失效
            }
            session = null;
        }
    }

    private String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
