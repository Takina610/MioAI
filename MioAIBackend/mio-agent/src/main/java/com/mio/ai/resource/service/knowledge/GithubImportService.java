package com.mio.ai.resource.service.knowledge;

import com.mio.ai.resource.model.vo.knowledge.GithubImportItemVO;
import com.mio.ai.resource.model.vo.knowledge.GithubPreviewVO;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/10/10
 * @description: GitHub 仓库内容导入知识库
 */
public interface GithubImportService {

    /**
     * 解析 GitHub 链接并返回可导入文件清单（不做任何落库动作）
     */
    GithubPreviewVO preview(Long userId, String url);

    /**
     * 将选中的仓库文件下载后写入知识库（R2 + document 记录，状态待处理），
     * 后续向量化复用既有流程
     *
     * @param kbId  知识库ID（校验归属）
     * @param userId 当前登录用户
     * @param url   与预览时一致的 GitHub 链接
     * @param paths 选中的仓库内文件路径
     */
    List<GithubImportItemVO> importToKnowledgeBase(Long kbId, Long userId, String url, List<String> paths);
}
