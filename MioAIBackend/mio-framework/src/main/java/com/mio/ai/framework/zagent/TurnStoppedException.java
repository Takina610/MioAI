package com.mio.ai.framework.zagent;

/**
 * 轮次被用户主动中断：引擎在步间/重试等待的取消检查点抛出，
 * 上层（MioBot）据此走"已停止"收尾——部分过程落库 + stopped 事件，不走兜底抢救。
 */
public final class TurnStoppedException extends RuntimeException {

    public TurnStoppedException() {
        super("turn stopped by user");
    }
}
