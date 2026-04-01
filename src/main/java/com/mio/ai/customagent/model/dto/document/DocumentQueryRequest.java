package com.mio.ai.customagent.model.dto.document;

import com.mio.ai.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/4/1
 * @description: 文档查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentQueryRequest extends PageRequest implements Serializable {

    /**
     * 知识库ID
     */
    private Long kbId;

    /**
     * 文件名（模糊查询）
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 状态
     */
    private Integer status;
}
