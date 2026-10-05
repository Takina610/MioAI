package com.mio.ai.bot.service;

import com.mio.ai.common.utils.JacksonUtil;
import com.mio.ai.framework.sandbox.SandboxFileTransfer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 附件删除的可靠异步队列（Redis List + ZSet，语义对齐 Redisson 的延迟队列）：
 * 消费失败按指数退避重试，进程崩溃后 inflight 消息自动恢复，超过重试上限记死信日志——
 * 不丢消息、不静默失败；删除本身幂等（文件已不存在视作成功），at-least-once 安全。
 * <p>为什么不直删：SFTP rm 是慢外网操作，异步化让前端撤回即时生效；
 * 为什么不用本地定时：后端非常驻，Redis 消息跨重启存活，重启后自动续删。
 */
@Slf4j
@Component
public class AttachmentDeleteQueue {

    /** 待处理队列（List，LPUSH 生产 / RPOP 消费） */
    private static final String QUEUE_KEY = "mio:attachment:delete:queue";
    /** 延迟重试区（ZSet，score = 到期时间戳 ms） */
    private static final String DELAY_KEY = "mio:attachment:delete:delay";
    /** 处理中区（ZSet，score = 取出时间戳 ms；崩溃恢复依据） */
    private static final String INFLIGHT_KEY = "mio:attachment:delete:inflight";
    /** 处理中超时：超过即视为进程崩溃，消息回队 */
    private static final long INFLIGHT_TIMEOUT_MS = 60_000;
    /** 最大尝试次数（含首次），超过记死信日志（文件由清理 cron 兜底） */
    private static final int MAX_ATTEMPTS = 5;

    private final StringRedisTemplate redis;
    private final SandboxFileTransfer fileTransfer;

    private Thread worker;
    private volatile boolean running;

    public AttachmentDeleteQueue(StringRedisTemplate redis, SandboxFileTransfer fileTransfer) {
        this.redis = redis;
        this.fileTransfer = fileTransfer;
    }

    /** 生产：入队即返回（同步校验路径，失败抛给调用方） */
    public void enqueue(String path) {
        if (!com.mio.ai.bot.model.dto.AttachmentItem.isStagedPath(path)) {
            throw new IllegalArgumentException("非法的附件路径");
        }
        enqueueMessage(path, 1);
    }

    private void enqueueMessage(String path, int attempts) {
        String message = JacksonUtil.writeValueAsString(Map.of("path", path, "attempts", attempts));
        redis.opsForList().leftPush(QUEUE_KEY, message);
    }

    // ---------- 生命周期：随应用启停 ----------

    @jakarta.annotation.PostConstruct
    public void start() {
        running = true;
        worker = new Thread(this::consumeLoop, "attachment-delete-consumer");
        worker.setDaemon(true);
        worker.start();
        log.info("附件删除队列消费者已启动");
    }

    @jakarta.annotation.PreDestroy
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    /** 消费循环：挪到期重试 → 恢复崩溃消息 → 取一条处理；空闲短睡轮询 */
    private void consumeLoop() {
        while (running) {
            try {
                sweepDelayed();
                sweepInflight();
                String message = redis.opsForList().rightPop(QUEUE_KEY);
                if (message == null) {
                    Thread.sleep(2000);
                    continue;
                }
                process(message);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("附件删除队列循环异常: {}", e.getMessage());
                sleepQuietly(5000);
            }
        }
    }

    /** 处理一条消息：SFTP 删除；成功出区，失败按指数退避重试或记死信 */
    private void process(String message) {
        Map<?, ?> payload = parse(message);
        if (payload == null) {
            return;
        }
        String path = String.valueOf(payload.get("path"));
        int attempts = payload.get("attempts") instanceof Number n ? n.intValue() : 1;
        long now = System.currentTimeMillis();
        redis.opsForZSet().add(INFLIGHT_KEY, message, now);
        try {
            if (!fileTransfer.available()) {
                throw new IllegalStateException("沙箱未启用");
            }
            fileTransfer.delete(path);
            redis.opsForZSet().remove(INFLIGHT_KEY, message);
            log.info("附件已删除: {}（第 {} 次尝试）", path, attempts);
        } catch (Exception e) {
            redis.opsForZSet().remove(INFLIGHT_KEY, message);
            if (attempts >= MAX_ATTEMPTS) {
                // 死信：只记日志，文件由 VPS 清理 cron 兜底删除
                log.error("附件删除重试耗尽，转入死信: path={}, attempts={}", path, attempts);
                return;
            }
            long backoff = Math.min(300_000, 5000L * (1L << (attempts - 1)));
            redis.opsForZSet().add(DELAY_KEY, messageOf(path, attempts + 1), now + backoff);
            log.info("附件删除失败将重试: path={}, next={}/{}，{}ms 后", path, attempts + 1, MAX_ATTEMPTS, backoff);
        }
    }

    /** 到期的延迟重试消息挪回主队列 */
    private void sweepDelayed() {
        Set<String> due = redis.opsForZSet().rangeByScore(DELAY_KEY, 0, System.currentTimeMillis());
        if (due == null || due.isEmpty()) {
            return;
        }
        for (String message : due) {
            if (redis.opsForZSet().remove(DELAY_KEY, message) > 0) {
                redis.opsForList().leftPush(QUEUE_KEY, message);
            }
        }
    }

    /** 崩溃恢复：处理中超过阈值的消息回队（删除幂等，重复执行安全） */
    private void sweepInflight() {
        Set<String> stale = redis.opsForZSet()
                .rangeByScore(INFLIGHT_KEY, 0, System.currentTimeMillis() - INFLIGHT_TIMEOUT_MS);
        if (stale == null || stale.isEmpty()) {
            return;
        }
        for (String message : stale) {
            if (redis.opsForZSet().remove(INFLIGHT_KEY, message) > 0) {
                redis.opsForList().leftPush(QUEUE_KEY, message);
                log.info("崩溃恢复：附件删除消息回队");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> parse(String message) {
        try {
            return JacksonUtil.readValue(message, Map.class);
        } catch (Exception e) {
            log.warn("附件删除消息解析失败，丢弃: {}", message);
            return null;
        }
    }

    private String messageOf(String path, int attempts) {
        return JacksonUtil.writeValueAsString(Map.of("path", path, "attempts", attempts));
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
