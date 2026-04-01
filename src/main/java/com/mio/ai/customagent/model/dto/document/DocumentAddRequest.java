package com.mio.ai.customagent.model.dto.document;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 文档创建请求
 */
@Data
public class DocumentAddRequest implements Serializable {

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
     * 文件大小
     */
    private Long fileSize;

    /**
     * 文件路径
     */
    private String filePath;
}
