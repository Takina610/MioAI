package com.mio.ai.superagent.model.enums;

/**
 * @author: Takina
 * @date: 2026/3/31 16:08
 * @description: 代理执行状态的枚举类
 */

public enum AgentState {

    /**
     * 空闲状态
     */
    IDLE,

    /**
     * 运行中状态
     */
    RUNNING,

    /**
     * 已完成状态
     */
    FINISHED,

    /**
     * 错误状态
     */
    ERROR
}
