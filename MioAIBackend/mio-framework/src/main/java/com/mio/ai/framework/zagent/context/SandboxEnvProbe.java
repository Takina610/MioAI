package com.mio.ai.framework.zagent.context;

import cn.hutool.core.util.StrUtil;
import com.mio.ai.framework.zagent.tools.SandboxFs;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 沙箱环境探测（zcode env-info/git 上下文的数据源）：
 * uname / git 分支与状态 / AGENTS.md 内容，5 分钟进程内缓存。
 */
public final class SandboxEnvProbe {

    private record EnvInfo(String osVersion, boolean gitRepo, String branch, String mainBranch,
                           String gitUser, String statusSnapshot, String recentCommits,
                           String agentsMd) {
    }

    private static final long CACHE_TTL_MS = 5 * 60_000;
    private static final Map<String, CacheSlot> CACHE = new ConcurrentHashMap<>();

    private record CacheSlot(long probedAt, EnvInfo info) {
    }

    private SandboxEnvProbe() {
    }

    public static String osVersion(SandboxFs fs) {
        return probe(fs).osVersion();
    }

    public static boolean isGitRepo(SandboxFs fs) {
        return probe(fs).gitRepo();
    }

    public static String gitContextLines(SandboxFs fs) {
        EnvInfo info = probe(fs);
        if (!info.gitRepo()) {
            return null;
        }
        return SystemPrompts.gitContextSection(info.branch(), info.mainBranch(), info.gitUser(),
                info.statusSnapshot(), info.recentCommits());
    }

    /** 工作目录内的 AGENTS.md（zcode request-user-context 的项目侧来源） */
    public static String agentsMd(SandboxFs fs) {
        return probe(fs).agentsMd();
    }

    private static EnvInfo probe(SandboxFs fs) {
        if (fs == null) {
            return new EnvInfo(null, false, null, null, null, null, null, null);
        }
        CacheSlot cached = CACHE.get(cacheKey(fs));
        if (cached != null && System.currentTimeMillis() - cached.probedAt() < CACHE_TTL_MS) {
            return cached.info();
        }
        EnvInfo info = doProbe(fs);
        if (CACHE.size() > 16) {
            CACHE.clear();
        }
        CACHE.put(cacheKey(fs), new CacheSlot(System.currentTimeMillis(), info));
        return info;
    }

    private static String cacheKey(SandboxFs fs) {
        return fs.workdirDisplay();
    }

    private static EnvInfo doProbe(SandboxFs fs) {
        String osVersion = firstLine(fs.exec("uname -sr", null, null).output());
        String insideRepo = fs.exec("git rev-parse --is-inside-work-tree 2>/dev/null", null, null).output();
        boolean gitRepo = insideRepo != null && insideRepo.strip().equals("true");
        if (!gitRepo) {
            return new EnvInfo(osVersion, false, null, null, null, null, null, readAgentsMd(fs));
        }
        String branch = firstLine(fs.exec("git rev-parse --abbrev-ref HEAD 2>/dev/null", null, null).output());
        String mainBranch = StrUtil.blankToDefault(
                firstLine(fs.exec("git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | "
                        + "sed 's@^refs/remotes/origin/@@'", null, null).output()),
                "main");
        String gitUser = firstLine(fs.exec("git config user.name 2>/dev/null", null, null).output());
        String status = fs.exec("git status --porcelain 2>/dev/null | head -n 30", null, null).output();
        String commits = fs.exec("git log --oneline -n 5 2>/dev/null", null, null).output();
        return new EnvInfo(osVersion, true, branch, mainBranch, gitUser,
                StrUtil.blankToDefault(status, null), StrUtil.blankToDefault(commits, null), readAgentsMd(fs));
    }

    private static String readAgentsMd(SandboxFs fs) {
        try {
            SandboxFs.FileStat stat = fs.stat("AGENTS.md");
            if (!stat.exists() || stat.size() > 64 * 1024) {
                return null;
            }
            String content = fs.readAll("AGENTS.md");
            return StrUtil.isBlank(content) ? null : content;
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstLine(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        List<String> lines = SandboxFs.splitLines(output);
        return lines.isEmpty() ? null : StrUtil.blankToDefault(lines.get(0), null);
    }
}
