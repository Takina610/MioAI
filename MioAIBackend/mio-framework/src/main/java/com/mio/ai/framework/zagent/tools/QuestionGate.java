package com.mio.ai.framework.zagent.tools;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户问答门闸（zcode AskUserQuestion 的运行时暂停点）：
 * 工具侧登记待答问题并阻塞等待；前端答题经 /bot/answer 解锁同一会话的挂起问题。
 * <p>每个会话同一时刻只允许一个待答问题（后到的提问直接失败，避免 UI 争抢）。
 */
public final class QuestionGate {

    private static final QuestionGate INSTANCE = new QuestionGate();

    public static QuestionGate instance() {
        return INSTANCE;
    }

    private QuestionGate() {
    }

    /** 一次提交给用户的答案（按问题下标） */
    public record Answer(int index, List<String> selections, String custom) {
    }

    /** 已登记的待答问题 */
    private static final class Pending {
        final String id;
        final List<Map<String, Object>> payload;
        final CompletableFuture<List<Answer>> future = new CompletableFuture<>();

        Pending(String id, List<Map<String, Object>> payload) {
            this.id = id;
            this.payload = payload;
        }
    }

    private final Map<String, Pending> byChat = new ConcurrentHashMap<>();

    /** 登记问题；该会话已有待答问题时返回 null（调用方转为工具失败） */
    public synchronized List<Map<String, Object>> register(
            String chatId, String id, List<Map<String, Object>> payload) {
        if (byChat.containsKey(chatId)) {
            return null;
        }
        byChat.put(chatId, new Pending(id, payload));
        return payload;
    }

    /** 用户提交答案；无匹配的待答问题时返回 false */
    public synchronized List<Map<String, Object>> resolve(String chatId, List<Answer> answers) {
        Pending pending = byChat.remove(chatId);
        if (pending == null) {
            return null;
        }
        pending.future.complete(answers);
        return pending.payload;
    }

    /** 工具侧等待答案（超时返回 null = 用户未作答） */
    public List<Answer> await(String chatId, long timeoutMs) {
        Pending pending = byChat.get(chatId);
        if (pending == null) {
            return null;
        }
        try {
            return pending.future.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            byChat.remove(chatId, pending);
            return null;
        }
    }

    /** 当前待答问题的事件负载（无则 null） */
    public List<Map<String, Object>> pendingPayload(String chatId) {
        Pending pending = byChat.get(chatId);
        return pending == null ? null : pending.payload;
    }
}
