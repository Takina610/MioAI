package com.mio.ai.framework.zagent.task;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.framework.zagent.tools.SandboxFs;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 沙箱脱离式后台 bash（任务引擎持久化的基石）：
 * 命令经 setsid+nohup 在沙箱侧独立会话运行，输出/退出码/PID 落 ~/.mio-tasks/&lt;taskId&gt;.*，
 * 后端重启（SSH 通道断开）不杀进程；TaskOutput/TaskStop 与轮次刷新经探针重连收尾。
 * <p>文件约定：&lt;taskId&gt;.sh 启动脚本（base64 落盘防注入）、.out 合并输出、.code 退出码、
 * .pid 会话首进程 PID（kill -- -pid 可灭整组）。
 */
@Slf4j
public final class DetachedBash {

    private static final String DIR = "~/.mio-tasks";
    private static final int OUTPUT_TAIL_CHARS = 64_000;
    private static final int LAUNCH_TIMEOUT_SECONDS = 30;

    /** setsid 可用性：首个任务探测一次，缺失时降级为纯 nohup（stop 只能杀主进程） */
    private static volatile Boolean setsidAvailable;

    private DetachedBash() {
    }

    public record ProbeResult(String status, Integer exitCode, String outputTail) {
    }

    /** 落脚本并启动；成功返回 true（pid 记入 task.sandboxPid） */
    public static boolean launch(SandboxFs fs, BackgroundTasks.Task task,
                                 String command, int timeoutSeconds) {
        String id = task.id;
        String script = "(" + command + ")\n"
                + "__mio_rc=$?\nprintf '%s' \"$__mio_rc\" > " + DIR + "/" + id + ".code\n";
        String b64 = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_8));
        SandboxFs.ExecResult setup = fs.exec(
                "mkdir -p " + DIR + " && echo " + b64 + " | base64 -d > " + DIR + "/" + id + ".sh",
                LAUNCH_TIMEOUT_SECONDS, null);
        if (setup.exitCode() == null || setup.exitCode() != 0) {
            log.warn("DetachedBash setup failed, task={}: exit={}, out={}", id, setup.exitCode(),
                    StrUtil.subPre(setup.output(), 300));
            return false;
        }
        String runner = (useSetsid(fs) ? "setsid " : "") + "nohup bash " + DIR + "/" + id + ".sh";
        // launch exec 的完整命令永远带 execRaw 前缀 `cd <home> && `——若不加 `true;` 断开，
        // `&` 会把整条 `cd && setsid...` 列表包进子壳后台执行，子壳等前台 setsid（即等命令跑完），
        // 抱着 SSH 通道管道直到超时。三点缺一不可：
        // ① `true;` 断开 && 绑定，让 `&` 只作用于 setsid 单条简单命令（无子壳等待）；
        // ② 三个标准流全部重定向（stdin=/dev/null），子进程不持有 SSH 通道管道；
        // ③ setsid 让 $! 即会话首进程（无作业控制的 shell 不 fork 组长，setsid 直接 exec）。
        String launch = "true; " + runner + " < /dev/null > " + DIR + "/" + id + ".out 2>&1 & "
                + "__mio_pid=$!; echo $__mio_pid > " + DIR + "/" + id + ".pid; echo $__mio_pid";
        SandboxFs.ExecResult result = fs.exec(launch, LAUNCH_TIMEOUT_SECONDS, null);
        Long pid = firstLong(result.output());
        if (result.exitCode() == null || result.exitCode() != 0 || pid == null) {
            log.warn("DetachedBash launch failed, task={}: exit={}, timedOut={}, out={}",
                    id, result.exitCode(), result.timedOut(), StrUtil.subPre(result.output(), 300));
            return false;
        }
        task.sandboxPid = pid;
        task.detached = true;
        task.deadlineAt = timeoutSeconds > 0
                ? System.currentTimeMillis() + timeoutSeconds * 1000L : 0;
        return true;
    }

    /**
     * 探针（一条命令同时读退出码/存活/输出尾部）。
     * status: done（.code 已写）/ running（pid 存活）/ gone（无码且进程不在）。
     *
     * @param waitSeconds 沙箱侧阻塞等待秒数（0 = 立即快照；阻塞轮询用 20~25 大幅减少 SSH 往返）
     */
    public static ProbeResult probe(SandboxFs fs, BackgroundTasks.Task task, int waitSeconds) {
        String id = task.id;
        int wait = Math.max(0, Math.min(waitSeconds, 30));
        String command = "D=" + DIR + "; I=" + id + "; i=0; "
                + "while [ $i -lt " + wait + " ]; do "
                + "[ -f \"$D/$I.code\" ] && break; "
                + "kill -0 \"$(cat \"$D/$I.pid\" 2>/dev/null)\" 2>/dev/null || break; "
                + "sleep 1; i=$((i+1)); done; "
                + "if [ -f \"$D/$I.code\" ]; then echo \"DONE:$(cat \"$D/$I.code\")\"; "
                + "elif [ -f \"$D/$I.pid\" ] && kill -0 \"$(cat \"$D/$I.pid\")\" 2>/dev/null; then echo RUNNING; "
                + "else echo GONE; fi; "
                + "tail -c " + OUTPUT_TAIL_CHARS + " \"$D/$I.out\" 2>/dev/null";
        SandboxFs.ExecResult result = fs.exec(command, LAUNCH_TIMEOUT_SECONDS + wait, null);
        if (result.exitCode() == null || result.exitCode() != 0) {
            return null;
        }
        String output = StrUtil.nullToEmpty(result.output());
        int firstLineEnd = output.indexOf('\n');
        String head = firstLineEnd < 0 ? output : output.substring(0, firstLineEnd);
        String tail = firstLineEnd < 0 ? "" : output.substring(firstLineEnd + 1);
        if (head.startsWith("DONE:")) {
            return new ProbeResult("done", firstInt(head.substring(5)), tail);
        }
        if (head.contains("RUNNING")) {
            return new ProbeResult("running", null, tail);
        }
        return new ProbeResult("gone", null, tail);
    }

    /** 终止：先整组 TERM 再 KILL（setsid 会话首进程的负 PID 即进程组） */
    public static void kill(SandboxFs fs, BackgroundTasks.Task task) {
        String id = task.id;
        // pid 文件缺失时退回注册时捕获的 PID
        String fallbackPid = task.sandboxPid != null ? String.valueOf(task.sandboxPid) : "";
        String command = "D=" + DIR + "; I=" + id + "; "
                + "P=$(cat \"$D/$I.pid\" 2>/dev/null); "
                + "[ -n \"$P\" ] || P=" + fallbackPid + "; "
                + "if [ -n \"$P\" ]; then "
                + "kill -TERM -- -$P 2>/dev/null; kill -TERM \"$P\" 2>/dev/null; "
                + "sleep 1; "
                + "kill -KILL -- -$P 2>/dev/null; kill -KILL \"$P\" 2>/dev/null; fi";
        fs.exec(command, LAUNCH_TIMEOUT_SECONDS, null);
    }

    /** 清理过期任务文件（保留天数由调用方决定；配合定时清理调用） */
    public static void cleanup(SandboxFs fs, int keepDays) {
        fs.exec("find " + DIR + " -type f -mtime +" + Math.max(1, keepDays) + " -delete 2>/dev/null",
                LAUNCH_TIMEOUT_SECONDS, null);
    }

    private static boolean useSetsid(SandboxFs fs) {
        Boolean available = setsidAvailable;
        if (available == null) {
            SandboxFs.ExecResult result = fs.exec("command -v setsid >/dev/null 2>&1 && echo y || echo n",
                    LAUNCH_TIMEOUT_SECONDS, null);
            available = result.output() != null && result.output().contains("y");
            setsidAvailable = available;
        }
        return available;
    }

    private static Long firstLong(String output) {
        if (output == null) {
            return null;
        }
        for (String token : output.strip().split("\\s+")) {
            try {
                return Long.parseLong(token);
            } catch (NumberFormatException ignored) {
                // 跳过非数字行
            }
        }
        return null;
    }

    private static Integer firstInt(String text) {
        try {
            return Integer.parseInt(text.strip());
        } catch (Exception e) {
            return null;
        }
    }
}
