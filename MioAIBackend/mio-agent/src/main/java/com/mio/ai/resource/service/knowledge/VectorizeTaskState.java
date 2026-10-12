package com.mio.ai.resource.service.knowledge;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 向量化任务的全局状态（KB 维度）：运行中标记、进度快照、取消标志。
 * 独立成组件是为了让知识库删除路径也能置取消标志（防删除与在途任务的孤儿向量竞态），
 * 而不必让 KnowledgeBaseService 与创建服务互相注入。
 * <p>任务模型：任务由第一个 SSE 连接触发，之后只跑一份（runningKbIds 防重入）；
 * 实时进度写入 progressMap 快照，任何时刻新开的 SSE 连接（刷新页面/网络波动重连/换设备）
 * 都以观察者身份从快照接管进度，与 Agent 会话的重连回放同构。服务重启后快照丢失，
 * 此时重连会走正常启动路径并幂等处理（只处理 status 0/1/3 的文档）。
 */
@Component
public class VectorizeTaskState {

    /**
     * 正在执行向量化任务的知识库集合：同一 KB 的任务全局只跑一份，
     * 后续 SSE 连接一律进入观察者模式
     */
    private final Set<Long> runningKbIds = ConcurrentHashMap.newKeySet();

    /**
     * 向量化进度快照（KB 维度）：任务线程写，所有 SSE 连接（发起者 + 观察者）读
     */
    private final Map<Long, VectorizeProgress> progressMap = new ConcurrentHashMap<>();

    /**
     * 向量化任务的中断标志：删除/取消入口置位，任务线程感知后停止并在收尾时
     * 按知识库清一遍向量（补杀检查点与落库之间在途写入的分块），最后自行清除标志
     */
    private final Map<Long, Boolean> cancellationFlags = new ConcurrentHashMap<>();

    /**
     * 尝试认领 KB 的执行权：true=认领成功（本连接成为任务发起者），false=已有任务在跑
     */
    public boolean tryAcquire(Long kbId) {
        return runningKbIds.add(kbId);
    }

    public void release(Long kbId) {
        runningKbIds.remove(kbId);
    }

    public boolean isRunning(Long kbId) {
        return runningKbIds.contains(kbId);
    }

    public VectorizeProgress snapshot(Long kbId) {
        return progressMap.get(kbId);
    }

    public void putSnapshot(Long kbId, VectorizeProgress progress) {
        progressMap.put(kbId, progress);
    }

    public void removeSnapshot(Long kbId) {
        progressMap.remove(kbId);
    }

    /**
     * 请求在途任务停止（删除/取消入口调用）。任务收尾时负责清标志；
     * 无任务在跑时残留的标志由下次任务启动时当作陈旧值清除
     */
    public void requestCancel(Long kbId) {
        cancellationFlags.put(kbId, true);
    }

    public boolean isCancelled(Long kbId) {
        return Boolean.TRUE.equals(cancellationFlags.get(kbId));
    }

    /**
     * 任务收尾：取走并清除取消标志，返回是否走过取消（true 时调用方应按 kbId 兜底清向量）
     */
    public boolean consumeCancellation(Long kbId) {
        return cancellationFlags.remove(kbId) != null;
    }

    /**
     * 向量化进度快照。done 为终态标志；failed/cancelled 细分终态类型。
     * 字段公开无妨：快照本身只在包内的任务与观察者之间流转
     */
    public static class VectorizeProgress {
        public volatile int total;
        public final AtomicInteger current = new AtomicInteger();
        public final AtomicInteger failedCount = new AtomicInteger();
        public volatile String message = "";
        public volatile String fileName;
        public volatile boolean done;
        public volatile boolean failed;
        public volatile boolean cancelled;
        public volatile String errorMessage;
    }
}
