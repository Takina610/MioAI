package com.mio.ai.resource.model.dto.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/11
 * @description: 命中测试请求（POST body，替代旧 GET 路径参数版：支持长查询与完整检索配置）
 */
@Data
public class SimilaritySearchRequest implements Serializable {

    /**
     * 查询内容
     */
    @NotBlank(message = "查询内容不能为空")
    @Size(max = 500, message = "查询内容长度不能超过500")
    private String query;

    /**
     * 可选；传入时只在该知识库内检索（需为所有者或公开知识库），
     * 不传时检索"本人全部知识库 + 公开知识库"
     */
    private Long kbId;

    /**
     * 返回数量上限（1~50，默认 5）
     */
    private Integer topK;

    /**
     * 相似度阈值（0~1）
     */
    private Double threshold;

    /**
     * 检索方式：vector / hybrid（默认 hybrid）
     */
    private String method;

    /**
     * 是否启用重排
     */
    private Boolean enableRerank;
}
