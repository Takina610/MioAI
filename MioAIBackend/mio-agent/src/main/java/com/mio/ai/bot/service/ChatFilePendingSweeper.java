package com.mio.ai.bot.service;

import com.mio.ai.bot.model.entity.ChatFileDO;
import com.mio.ai.framework.sandbox.SandboxFileTransfer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 待删文件兜底扫描：chat_file 中 status=待删除 的行就是"删除意图尚未兑现"的持久凭证
 * （入队失败、重试耗尽死信、进程崩溃丢消息都汇聚到这里）。定时把待删行重新入队，
 * 直到沙箱删除与台账清理双双完成、行被消费方删除为止。
 * <p>孤儿判断以台账为准，不在沙箱侧猜文件归属：所有暂存文件在上传/产出时强制登记，
 * 沙箱里"有文件、无登记"只可能是本功能上线前的历史遗留，不做扫描对账。
 */
@Slf4j
@Component
public class ChatFilePendingSweeper {

    /** 单轮扫描重入队上限（异常积压时分批收敛，避免一次塞爆队列） */
    private static final int SWEEP_LIMIT = 500;

    private final ChatFileService chatFileService;
    private final AttachmentDeleteQueue deleteQueue;
    private final SandboxFileTransfer fileTransfer;

    public ChatFilePendingSweeper(ChatFileService chatFileService,
                                  AttachmentDeleteQueue deleteQueue,
                                  SandboxFileTransfer fileTransfer) {
        this.chatFileService = chatFileService;
        this.deleteQueue = deleteQueue;
        this.fileTransfer = fileTransfer;
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000, initialDelay = 30_000)
    public void sweepPendingDeletes() {
        if (!fileTransfer.available()) {
            return;
        }
        List<ChatFileDO> pending = chatFileService.listPending(SWEEP_LIMIT);
        if (pending.isEmpty()) {
            return;
        }
        int enqueued = 0;
        for (ChatFileDO row : pending) {
            try {
                deleteQueue.enqueue(row.getPath());
                enqueued++;
            } catch (Exception e) {
                log.warn("待删文件重入队失败, path={}: {}", row.getPath(), e.getMessage());
            }
        }
        if (enqueued > 0) {
            log.info("待删文件兜底扫描：重入队 {} 个（剩余见下轮）", enqueued);
        }
    }
}
