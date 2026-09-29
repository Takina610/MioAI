package com.mio.ai.superagent.tools.CommonTools;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author: Takina
 * @date: 2026/9/29
 * @description: 终端命令安全策略（白名单）
 * <p>MioManus 的终端工具此前直接执行 LLM 给出的任意命令，存在被诱导执行恶意命令的风险。
 * 现在改为白名单机制：
 * <ol>
 *   <li>命令首个词必须在白名单内（默认仅开放只读/开发类命令）；</li>
 *   <li>拒绝命令链（&amp;、||、|、;、&gt;、&lt;、反引号、$( 等）以防白名单命令被拼接绕过。</li>
 * </ol>
 * 白名单可通过配置项 {@code mio.ai.tools.terminal.allowed-commands} 覆盖（逗号分隔）。
 */
public class TerminalCommandPolicy {

    /**
     * 默认白名单：只读查询与开发工具链，覆盖"查看目录/查看文件/编译运行"等常见任务
     */
    public static final String DEFAULT_ALLOWED = String.join(",",
            "dir", "cd", "tree", "type", "echo", "where", "findstr", "hostname", "ver", "whoami",
            "systeminfo", "ipconfig", "tasklist", "ping",
            "java", "javac", "jar", "mvn", "gradle",
            "python", "pip", "node", "npm", "npx");

    /**
     * 命令链/重定向等元字符，出现即拒绝，防止白名单命令被拼接成任意命令
     */
    private static final char[] BLOCKED_CHARS = {'&', '|', ';', '>', '<', '`'};

    private static final String COMMAND_SUBSTITUTION = "$(";

    private final Set<String> allowedCommands;

    public TerminalCommandPolicy(Set<String> allowedCommands) {
        this.allowedCommands = allowedCommands;
    }

    public static TerminalCommandPolicy defaultPolicy() {
        return fromConfig(DEFAULT_ALLOWED);
    }

    public static TerminalCommandPolicy fromConfig(String commaSeparated) {
        Set<String> commands = new LinkedHashSet<>();
        if (commaSeparated != null && !commaSeparated.isBlank()) {
            Arrays.stream(commaSeparated.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toLowerCase())
                    .forEach(commands::add);
        }
        return new TerminalCommandPolicy(commands);
    }

    /**
     * 校验命令是否允许执行
     *
     * @return null 表示允许；否则返回拒绝原因
     */
    public String checkAllowed(String command) {
        if (command == null || command.isBlank()) {
            return "命令为空";
        }
        String normalized = stripQuotes(command.trim());
        if (containsBlockedChar(normalized)) {
            return "命令包含禁止的管道/链式/重定向字符（& | ; > < ` $( ），仅允许执行单条简单命令";
        }
        String firstToken = firstToken(normalized);
        if (firstToken.isEmpty()) {
            return "无法识别命令";
        }
        if (!allowedCommands.contains(firstToken)) {
            return "命令 \"" + firstToken + "\" 不在允许列表内，允许的命令：" + allowedCommands;
        }
        return null;
    }

    public Set<String> getAllowedCommands() {
        return allowedCommands;
    }

    private boolean containsBlockedChar(String command) {
        for (char c : BLOCKED_CHARS) {
            if (command.indexOf(c) >= 0) {
                return true;
            }
        }
        return command.contains(COMMAND_SUBSTITUTION);
    }

    private String firstToken(String command) {
        int spaceIndex = command.indexOf(' ');
        String token = spaceIndex > 0 ? command.substring(0, spaceIndex) : command;
        return stripQuotes(token).toLowerCase();
    }

    private String stripQuotes(String s) {
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
