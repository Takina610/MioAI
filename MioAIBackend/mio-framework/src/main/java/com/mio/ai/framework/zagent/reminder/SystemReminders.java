package com.mio.ai.framework.zagent.reminder;

import com.mio.ai.framework.zagent.history.ConversationState;
import com.mio.ai.framework.zagent.task.BackgroundTasks;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * system-reminder 机制（zcode runtime-reminders 的对位移植）：
 * 日期变化、todo 提醒（≥10 轮未写清单）、后台任务通知——均以 user 角色
 * &lt;system-reminder&gt; 附件注入请求历史。
 */
public final class SystemReminders {

    private static final Map<String, String> LAST_DATE_BY_CHAT = new ConcurrentHashMap<>();

    private SystemReminders() {
    }

    /** 本轮应注入的提醒附件（注入后即记账，避免重复） */
    public static List<String> collectTurnReminders(String chatId, ConversationState state,
                                                     BackgroundTasks tasks) {
        List<String> reminders = new ArrayList<>();
        reminders.addAll(tasks.drainNotifications(chatId));
        String today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String lastSeen = LAST_DATE_BY_CHAT.put(chatId, today);
        if (lastSeen != null && !lastSeen.equals(today)) {
            reminders.add("The date has changed. Today's date is now " + today + ".");
        }
        if (state.shouldNudgeTodos()) {
            state.markTodoNudged();
            reminders.add("""
                    You have tools available to create and update a task list. Maintaining a visible task list \
                    helps the user understand progress and keeps work organized. Consider using TodoWrite to \
                    create or update your task list for the current work, then continue with the task.""");
        }
        return reminders;
    }

    /** 模型步间提醒（zcode per-request：每步请求前检查） */
    public static List<String> collectStepReminders(ConversationState state) {
        List<String> reminders = new ArrayList<>();
        if (state.shouldNudgeTodos()) {
            state.markTodoNudged();
            reminders.add("""
                    You have tools available to create and update a task list. Maintaining a visible task list \
                    helps the user understand progress and keeps work organized. Consider using TodoWrite to \
                    create or update your task list for the current work, then continue with the task.""");
        }
        return reminders;
    }
}
