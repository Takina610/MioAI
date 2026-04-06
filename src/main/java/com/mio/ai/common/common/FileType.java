package com.mio.ai.common.common;

import lombok.Getter;

/**
 * @author: Takina
 * @date: 2026/4/2 16:11
 * @description: 文件类型枚举
 */

@Getter
public enum FileType {

    USER_AVATAR("avatars/user", "用户头像",
            new String[]{"jpg", "jpeg", "png", "gif", "webp"},
            20 * 1024 * 1024), // 20MB

    AGENT_AVATAR("avatars/agent", "智能体头像",
            new String[]{"jpg", "jpeg", "png", "gif", "webp"},
            20 * 1024 * 1024),

    KNOWLEDGE_FILE("knowledge_base", "知识库文件",
            new String[]{"pdf", "md", "markdown", "txt", "doc", "docx"},
            50 * 1024 * 1024), // 50MB

    PDF_FILE("pdf_files", "PDF文件",
            new String[]{"pdf"},
            50 * 1024 * 1024); // 50MB

    private final String basePath;
    private final String description;
    private final String[] allowedExtensions;
    private final long maxSize;

    FileType(String basePath, String description, String[] allowedExtensions, long maxSize) {
        this.basePath = basePath;
        this.description = description;
        this.allowedExtensions = allowedExtensions;
        this.maxSize = maxSize;
    }

    /**
     * 检查文件扩展名是否允许
     */
    public boolean isExtensionAllowed(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return false;
        }
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        for (String allowed : allowedExtensions) {
            if (allowed.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }

    public boolean needEntityFolder() {
        return this == KNOWLEDGE_FILE;
    }
}
