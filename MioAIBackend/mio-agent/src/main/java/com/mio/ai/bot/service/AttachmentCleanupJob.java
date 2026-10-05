package com.mio.ai.bot.service;

import com.mio.ai.framework.sandbox.SandboxFileTransfer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 附件暂存清理：uploads/outputs 都是暂存性质（沙箱磁盘有限），
 * 每天凌晨删掉超过 7 天的文件与空目录。
 */
@Slf4j
@Component
public class AttachmentCleanupJob {

    private static final int KEEP_DAYS = 7;

    private final SandboxFileTransfer fileTransfer;

    public AttachmentCleanupJob(SandboxFileTransfer fileTransfer) {
        this.fileTransfer = fileTransfer;
    }

    @Scheduled(cron = "0 30 4 * * ?")
    public void cleanup() {
        if (!fileTransfer.available()) {
            return;
        }
        try {
            fileTransfer.cleanupOld(KEEP_DAYS);
            log.info("附件暂存清理完成（>{} 天）", KEEP_DAYS);
        } catch (Exception e) {
            log.warn("附件暂存清理失败: {}", e.getMessage());
        }
    }
}
