package com.mio.ai.framework.zagent.tools;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 读文件状态（zcode read-file-state 的对位移植）：
 * Read/Write/Edit 共享的"最后见过的事实"，支撑防重复读与写前新鲜度门禁。
 * <p>按会话缓存于进程内（重启后失效，门禁自然放行，不致误拦）。
 */
public final class ReadFileState {

    /** 一次读取形成的文件视图 */
    public record FileView(long mtimeSec, long size, int startLine, int endLine, boolean partial) {
    }

    private static final int MAX_CHATS = 200;
    private static final Map<String, ReadFileState> BY_CHAT = new ConcurrentHashMap<>();

    private final Map<String, FileView> views = new ConcurrentHashMap<>();

    public static ReadFileState forChat(String chatId) {
        if (BY_CHAT.size() > MAX_CHATS) {
            BY_CHAT.clear();
        }
        return BY_CHAT.computeIfAbsent(chatId, key -> new ReadFileState());
    }

    public FileView get(String path) {
        return views.get(path);
    }

    public void put(String path, FileView view) {
        views.put(path, view);
    }

    public void remove(String path) {
        views.remove(path);
    }

    /** zcode 新鲜度判定：mtime 前进或大小变化即视为外部改动 */
    public boolean isStale(String path, long currentMtimeSec, long currentSize) {
        FileView view = views.get(path);
        if (view == null) {
            return false;
        }
        return view.mtimeSec() != currentMtimeSec || view.size() != currentSize;
    }
}
