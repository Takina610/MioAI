package com.mio.ai.customagent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 知识库实体类
 */
@Data
@TableName("knowledge_base")
public class KnowledgeBase implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 知识库名称
     */
    private String name;

    /**
     * 知识库描述
     */
    private String description;

    /**
     * 状态（0-创建中 1-正常 2-已禁用）
     */
    private Integer status;

    /**
     * 是否公开（0-私有 1-公开）
     */
    private Integer isPublic;

    /**
     * 文档数量
     */
    private Integer documentCount;

    /**
     * 存储大小（字节）
     */
    private Long storageSize;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic
    private Integer isDeleted;
}
