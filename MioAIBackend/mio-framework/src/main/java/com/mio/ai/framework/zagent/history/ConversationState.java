package com.mio.ai.framework.zagent.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 一次引擎运行持有的对话状态（zcode MessageHistory + 会话级计数的对位移植）。
 * <p>条目列表是发给模型的完整请求历史；任务清单与提醒计数驱动 system-reminder。
 */
public final class ConversationState {

    private final List<ConversationEntry> entries = new ArrayList<>();
    private final List<TodoItem> todos = new ArrayList<>();
    /** 自上次 TodoWrite 以来的带工具助手轮数（todo 提醒阈值用，zcode 为 10） */
    private int toolTurnsSinceTodoWrite;
    /** 自上次 todo 提醒以来的带工具助手轮数（避免重复提醒） */
    private int toolTurnsSinceTodoReminder;

    public List<ConversationEntry> entries() {
        return entries;
    }

    public void add(ConversationEntry entry) {
        entries.add(entry);
    }

    /** 压缩/回滚后整体替换请求历史 */
    public void replaceAll(List<ConversationEntry> replacement) {
        entries.clear();
        entries.addAll(replacement);
    }

    public List<TodoItem> todos() {
        return todos;
    }

    public void setTodos(List<TodoItem> replacement) {
        todos.clear();
        if (replacement != null) {
            todos.addAll(replacement);
        }
        toolTurnsSinceTodoWrite = 0;
    }

    /** 一轮带工具调用的模型步结束：推进提醒计数 */
    public void noteToolTurn() {
        toolTurnsSinceTodoWrite++;
        toolTurnsSinceTodoReminder++;
    }

    /** zcode todo 提醒触发条件：≥10 轮未写清单且 ≥10 轮未提醒过 */
    public boolean shouldNudgeTodos() {
        return toolTurnsSinceTodoWrite >= 10 && toolTurnsSinceTodoReminder >= 10;
    }

    public void markTodoNudged() {
        toolTurnsSinceTodoReminder = 0;
    }

    /** 估算当前请求历史的 token 量（zcode：字符/4） */
    public long estimateTokens() {
        return entries.stream().mapToLong(ConversationEntry::estimateTokens).sum();
    }

    /** 最后一条真实用户输入（压缩模板等场景使用） */
    public Optional<ConversationEntry> lastUserEntry() {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).kind == ConversationEntry.Kind.USER) {
                return Optional.of(entries.get(i));
            }
        }
        return Optional.empty();
    }
}
