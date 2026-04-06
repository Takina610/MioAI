package com.mio.ai.customagent.service;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * @author: Takina
 * @date: 2026/4/5
 * @description: 知识库创建服务接口
 */
public interface KnowledgeBaseCreateService {

    /**
     * 上传文件到R2并保存文档记录
     * @param kbId 知识库ID
     * @param userId 用户ID
     * @param files 文件列表
     * @return 上传结果列表
     */
    List<Map<String, Object>> uploadFiles(Long kbId, Long userId, MultipartFile[] files);

    /**
     * 向量化处理文件
     * @param kbId 知识库ID
     * @param userId 用户ID
     * @param emitter SSE发射器
     */
    void vectorizeFiles(Long kbId, Long userId, SseEmitter emitter);

    /**
     * 取消创建并清理资源
     * @param kbId 知识库ID
     * @param userId 用户ID
     */
    void cancelCreation(Long kbId, Long userId);
}
