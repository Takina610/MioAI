package com.mio.ai.resource.model.vo.skill;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: zip 安装结果
 */
@Data
public class SkillZipInstallVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 成功安装的技能数
     */
    private int installedCount;

    /**
     * 跳过的技能及原因
     */
    private List<SkippedItem> skipped;

    @Data
    public static class SkippedItem implements Serializable {

        private static final long serialVersionUID = 1L;

        private String name;

        private String reason;
    }
}
