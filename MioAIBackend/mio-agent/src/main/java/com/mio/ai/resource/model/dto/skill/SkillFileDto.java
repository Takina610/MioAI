package com.mio.ai.resource.model.dto.skill;

import com.mio.ai.common.aop.annotation.XssClean;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: 技能附属文件（相对技能根目录的路径 + 文本内容）
 */
@Data
public class SkillFileDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 相对路径（如 scripts/run.py）
     */
    @NotBlank(message = "文件路径不能为空")
    @Size(max = 255, message = "文件路径过长")
    @XssClean(mode = "strict")
    private String path;

    /**
     * 文本内容
     */
    @Size(max = 400_000, message = "文件内容过大")
    private String content;
}
