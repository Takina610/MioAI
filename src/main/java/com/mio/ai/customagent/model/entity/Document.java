package com.mio.ai.customagent.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 文档实体类
 */
@Data
@TableName("document")
public class Document implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 知识库ID
     */
    private Long kbId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 状态（0-待处理 1-处理中 2-已完成 3-失败）
     */
    private Integer status;

    /**
     * 分块数量
     */
    private Integer chunkCount;

    /**
     * 错误信息
     */
    private String errorMsg;

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
}
