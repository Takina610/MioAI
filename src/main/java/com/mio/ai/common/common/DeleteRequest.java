package com.mio.ai.common.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/3/28 11:21
 * @description: 通用的删除请求类
 */

@Data
public class DeleteRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
