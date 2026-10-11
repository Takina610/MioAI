package com.mio.ai.framework.zagent.hooks;

import com.mio.ai.framework.zagent.tools.ToolContext;
import com.mio.ai.framework.zagent.tools.ToolRegistry;

import java.util.List;

/**
 * 工具调用钩子（pi HookDefinition beforeToolCall/afterToolCall 的对位移植）：
 * 引擎核心不再硬编码横切关注点，审计/权限/限流等以钩子插件形式挂入注册表。
 * <p>beforeToolCall 可拦截（任一钩子 block 即终止执行，拦截原因作为工具错误信封
 * 返回给模型）；afterToolCall 观察最终结果（含失败/超时/被拦截的调用）。
 */
public interface AgentHooks {

    /** 一次待执行的工具调用快照（argumentsJson 为模型输出的原始参数 JSON） */
    record ToolCall(String id, String name, String argumentsJson) {
    }

    /** beforeToolCall 决策：放行或拦截（拦截理由对模型可见） */
    record Decision(boolean blocked, String reason) {

        public static Decision proceed() {
            return new Decision(false, null);
        }

        public static Decision block(String reason) {
            return new Decision(true, reason == null || reason.isBlank() ? "blocked by hook" : reason);
        }
    }

    /** 工具执行前：返回 block 决策可拦截本次调用 */
    default Decision beforeToolCall(ToolCall call, ToolContext context) {
        return Decision.proceed();
    }

    /** 工具执行后（含失败/超时/被拦截）：结果与耗时为注册表的最终裁定 */
    default void afterToolCall(ToolCall call, ToolRegistry.Executed result, ToolContext context) {
    }

    /** 组合多个钩子：before 全部放行才放行，after 逐个通知（任一钩子抛异常不影响执行链） */
    static AgentHooks chain(List<AgentHooks> hooks) {
        List<AgentHooks> safe = hooks == null ? List.of()
                : hooks.stream().filter(java.util.Objects::nonNull).toList();
        if (safe.isEmpty()) {
            return new AgentHooks() {
            };
        }
        if (safe.size() == 1) {
            return safe.get(0);
        }
        return new AgentHooks() {
            @Override
            public Decision beforeToolCall(ToolCall call, ToolContext context) {
                for (AgentHooks hook : safe) {
                    try {
                        Decision decision = hook.beforeToolCall(call, context);
                        if (decision != null && decision.blocked()) {
                            return decision;
                        }
                    } catch (Exception ignored) {
                        // 钩子故障不放大于执行链
                    }
                }
                return Decision.proceed();
            }

            @Override
            public void afterToolCall(ToolCall call, ToolRegistry.Executed result, ToolContext context) {
                for (AgentHooks hook : safe) {
                    try {
                        hook.afterToolCall(call, result, context);
                    } catch (Exception ignored) {
                        // 审计失败不影响主流程
                    }
                }
            }
        };
    }
}
