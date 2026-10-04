package com.mio.ai.framework.agent;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 系统提示词组装（参考 pi 的分 section 结构 / codex 的环境上下文注入）：
 * 身份（自定义或默认）+ 核心纪律（classpath:prompts/agent-core.txt）+ 环境信息块 + 调用方附加上下文。
 * prompt 资源外置，便于不碰代码调整 Agent 行为。
 */
@Slf4j
public final class AgentPrompts {

    private static final String CORE = loadCore();

    private AgentPrompts() {
    }

    /** 组装完整系统提示词 */
    public static String build(String identityPrompt, String extraContext, boolean sandboxEnabled, String workdir) {
        StringBuilder sb = new StringBuilder();
        sb.append(StrUtil.isNotBlank(identityPrompt) ? identityPrompt.strip() : defaultIdentity());
        sb.append("\n\n").append(CORE);
        sb.append("\n\n").append(environmentSection(sandboxEnabled, workdir));
        if (StrUtil.isNotBlank(extraContext)) {
            sb.append("\n\n").append(extraContext.strip());
        }
        return sb.toString();
    }

    private static String defaultIdentity() {
        return "你是 MioBot，MioAI 的智能助手，一个拥有真实 Linux 沙箱与通用工具的完整 Agent："
                + "自主规划任务、组合工具、根据结果迭代执行，直到真正完成用户的需求。";
    }

    private static String environmentSection(boolean sandboxEnabled, String workdir) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        String[] weekDays = {"一", "二", "三", "四", "五", "六", "日"};
        String time = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + " 星期" + weekDays[now.getDayOfWeek().getValue() - 1];
        StringBuilder sb = new StringBuilder("# 环境信息\n- 当前时间：").append(time).append("（北京时间）");
        if (sandboxEnabled) {
            sb.append("\n- 沙箱：一台真实的 Linux 服务器，工作目录 ").append(workdir)
              .append("（内容跨命令持久）。可用 runCommand 执行命令、readFile/writeFile/editFile 操作文件、")
              .append("glob/grep 搜索；自带 python3，无 sudo");
        }
        return sb.toString();
    }

    private static String loadCore() {
        try (InputStream in = AgentPrompts.class.getResourceAsStream("/prompts/agent-core.txt")) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
        } catch (Exception e) {
            log.warn("加载 Agent 核心提示词失败，使用内置兜底", e);
        }
        return """
                # 工作方式
                - 思考→行动→观察循环：需要外部信息或真实执行时调用工具，拿到结果决定下一步。
                - 自身知识能回答的（概念、介绍、创作、翻译、代码）直接回答，不调用工具。
                - 工具失败分析原因后重试或换方案，不编造结果。
                - 能给出完整回答时停止调用工具，直接输出最终回答。""";
    }
}
