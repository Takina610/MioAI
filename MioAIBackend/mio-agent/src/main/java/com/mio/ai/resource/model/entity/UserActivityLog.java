package com.mio.ai.resource.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户执行日志实体（user_activity_log）：控制台写操作（POST/PUT/DELETE）经 AOP 落库，
 * 登录/注册经事件落库——构成每个用户的完整执行履历。
 */
@Data
@TableName("user_activity_log")
public class UserActivityLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（未登录的写操作为 NULL）
     */
    private Long userId;

    /**
     * 业务模块（agent/knowledge/mcp/skill/model/chat/auth 等）
     */
    private String module;

    /**
     * 执行动作（方法名或 login/register 等语义动作）
     */
    private String action;

    /**
     * HTTP 方法
     */
    private String httpMethod;

    /**
     * 请求路径
     */
    private String path;

    /**
     * 入参摘要（JSON 截断）
     */
    private String summary;

    /**
     * 是否成功（1-成功 0-失败）
     */
    private Integer success;

    /**
     * 失败摘要
     */
    private String errorMsg;

    /**
     * 执行耗时（毫秒）
     */
    private Long costMs;

    /**
     * 客户端 IP
     */
    private String ip;

    /**
     * 创建时间
     */
    private Date createTime;
}
