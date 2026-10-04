package com.mio.ai.framework.tools.sandbox;

import cn.hutool.core.util.StrUtil;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/4
 * @description: SSH 沙箱客户端：在独立 Linux 服务器的固定工作目录内执行命令/写文件。
 * <p>每条命令独立连接（Agent 调用频率低，简单可靠优先）；输出尾部截断，
 * 自毁式命令做尽力拦截——单用户毕设沙箱的定位是"可自由实验"，不是高安全隔离。
 */
@Slf4j
public class SshSandboxClient {

    /** 尽力拦截的自毁/破坏式命令片段（小写匹配） */
    private static final List<String> BLOCKED_FRAGMENTS = List.of(
            "mkfs", "shutdown", "reboot", "poweroff", "halt", "init 0", "init 6",
            ":(){", "fork bomb", "dd if=/dev/", ">/dev/sd", "rm -rf /", "rm -fr /");

    private final SandboxProperties props;

    public SshSandboxClient(SandboxProperties props) {
        this.props = props;
    }

    /**
     * 在沙箱工作目录执行命令，返回 stdout+stderr（尾部截断）与退出码
     */
    public String run(String command, Integer timeoutSeconds) {
        String rejection = guard(command);
        if (rejection != null) {
            return rejection;
        }
        int timeout = timeoutSeconds != null && timeoutSeconds > 0
                ? Math.min(timeoutSeconds, 600) : props.getExecTimeoutSeconds();
        return exec("mkdir -p " + homeDir() + " && cd " + homeDir() + " && " + command, timeout);
    }

    /**
     * 把文件写入沙箱工作目录（base64 传输避免引号转义问题），返回字节数确认
     */
    public String writeFile(String fileName, String content) {
        if (StrUtil.isBlank(fileName) || fileName.startsWith("/") || fileName.contains("..")) {
            return "文件名必须是工作目录内的相对路径: " + fileName;
        }
        String encoded = Base64.getEncoder()
                .encodeToString((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        String command = "mkdir -p " + homeDir() + " && cd " + homeDir()
                + " && mkdir -p " + quote(dirPart(fileName))
                + " && printf %s '" + encoded + "' | base64 -d > " + quote(fileName)
                + " && echo wrote $(wc -c < " + quote(fileName) + ") bytes";
        return exec(command, props.getExecTimeoutSeconds());
    }

    private String exec(String command, int timeoutSeconds) {
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(props.getUser(), props.getHost(), props.getPort());
            configureAuth(jsch, session);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(props.getConnectTimeoutMs());
            try {
                return runChannel(session, command, timeoutSeconds);
            } finally {
                session.disconnect();
            }
        } catch (Exception e) {
            log.warn("沙箱命令执行失败: {}", e.getMessage());
            return "沙箱执行出错: " + e.getMessage();
        }
    }

    private void configureAuth(JSch jsch, Session session) throws Exception {
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
    private String runChannel(Session session, String command, int timeoutSeconds) throws Exception {
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
            while (in.available() > 0) {
                int n = in.read(buffer);
                if (n < 0) {
                    break;
                }
                appendCapped(out, buffer, n);
            }
            Thread.sleep(30);
        }
        while (in.available() > 0) {
            int n = in.read(buffer);
            if (n < 0) {
                break;
            }
            appendCapped(out, buffer, n);
        }
        Integer exitCode = channel.isClosed() ? channel.getExitStatus() : null;
        channel.disconnect();

        String output = out.toString(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        if (output.length() > props.getMaxOutputChars()) {
            sb.append("[输出过长，仅保留末尾 ").append(props.getMaxOutputChars())
              .append(" 字符（共 ").append(output.length()).append(" 字符）]\n")
              .append(output.substring(output.length() - props.getMaxOutputChars()));
        } else {
            sb.append(output);
        }
        if (timedOut) {
            sb.append("\n[命令超过 ").append(timeoutSeconds).append(" 秒被强制终止]");
        } else if (exitCode != null && exitCode != 0) {
            sb.append("\n[退出码: ").append(exitCode).append("]");
        }
        return sb.toString();
    }

    /** 输出内存上限：超过 2 倍展示上限后丢弃中段，只留末尾 */
    private void appendCapped(ByteArrayOutputStream out, byte[] buffer, int n) {
        if (out.size() < props.getMaxOutputChars() * 2L) {
            out.write(buffer, 0, n);
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

    /** 工作目录的 $HOME 前缀形式（双引号保留变量展开，相对 workdir 一律落到家目录下） */
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

    private String dirPart(String fileName) {
        int slash = fileName.lastIndexOf('/');
        return slash > 0 ? fileName.substring(0, slash) : ".";
    }

    private String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
