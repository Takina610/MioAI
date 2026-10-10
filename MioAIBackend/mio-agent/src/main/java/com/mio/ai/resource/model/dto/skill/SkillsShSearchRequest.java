package com.mio.ai.resource.model.dto.skill;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: skills.sh 搜索请求
 */
@Data
public class SkillsShSearchRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "搜索关键词不能为空")
    @Size(max = 100, message = "搜索关键词过长")
    @XssClean(mode = "strict")
    private String query;

    private Integer limit;

    private Integer offset;
}
