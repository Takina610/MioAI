package com.mio.ai.common.utils;

/**
 * @author: Takina
 * @date: 2026/4/2 15:50
 * @description: R2存储上传文件工具
 */

import com.mio.ai.common.common.FileType;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.io.IOException;

@Component
public class R2Util {

    @Autowired
    private S3Client r2Client;

    @Value("${r2.bucket-name}")
    private String bucketName;

    @Value("${r2.cdn-domain}")
    private String cdnDomain;

    /**
     * 上传文件（覆盖模式）
     * @param file 文件
     * @param fileType 文件类型枚举
     * @param entityId 实体ID（如用户ID、智能体ID、知识库ID），用于作为文件名实现覆盖
     * @return 文件访问URL
     * @throws IOException 上传异常
     */
    public String uploadFile(MultipartFile file,
                             FileType fileType,
                             String entityId) throws IOException {
        validateFile(file, fileType);
        String fileKey = buildFileKey(file, fileType, entityId);

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(file.getContentType())
                    .build();

            r2Client.putObject(putRequest, RequestBody.fromBytes(file.getBytes()));
            return cdnDomain + "/" + fileKey;

        } catch (S3Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传文件到R2失败: " + e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * 上传本地文件
     * @param filePath 本地文件路径
     * @param fileType 文件类型枚举
     * @param entityId 实体ID
     * @return 文件访问URL
     * @throws IOException 上传异常
     */
    public String uploadLocalFile(String filePath,
                                  FileType fileType,
                                  String entityId) throws IOException {
        java.io.File file = new java.io.File(filePath);
        if (!file.exists()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不存在: " + filePath);
        }

        String fileName = file.getName();
        String extension = getFileExtension(fileName);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileKey = fileType.getBasePath() + "/" + entityId + "_" + timestamp + "." + extension;

        String contentType = "application/octet-stream";
        if ("pdf".equalsIgnoreCase(extension)) {
            contentType = "application/pdf";
        } else if ("jpg".equalsIgnoreCase(extension) || "jpeg".equalsIgnoreCase(extension)) {
            contentType = "image/jpeg";
        } else if ("png".equalsIgnoreCase(extension)) {
            contentType = "image/png";
        }

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .contentType(contentType)
                    .build();

            r2Client.putObject(putRequest, RequestBody.fromFile(file));
            return cdnDomain + "/" + fileKey;

        } catch (S3Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传文件到R2失败: " + e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * 删除文件
     * @param fileUrl 文件URL
     * @throws IOException 删除异常
     */
    public void deleteFile(String fileUrl) throws IOException {
        try {
            String fileKey = extractKeyFromUrl(fileUrl);
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();
            r2Client.deleteObject(deleteRequest);
        } catch (S3Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除文件失败: " + e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String fileKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();
            r2Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            return false;
        }
    }

    /**
     * 下载文件内容
     * @param fileUrl 文件URL
     * @return 文件内容字节数组
     */
    public byte[] downloadFile(String fileUrl) {
        try {
            String fileKey = extractKeyFromUrl(fileUrl);
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileKey)
                    .build();
            
            return r2Client.getObjectAsBytes(getRequest).asByteArray();
        } catch (S3Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载文件失败: " + e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file, FileType fileType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }

        // 检查文件大小
        if (file.getSize() > fileType.getMaxSize()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, String.format(
                    "文件大小超过限制: %s (最大 %.2f MB)",
                    fileType.getDescription(),
                    fileType.getMaxSize() / (1024.0 * 1024.0)
            ));
        }

        // 检查文件扩展名
        if (!fileType.isExtensionAllowed(originalFilename)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, String.format(
                    "不支持的文件类型: %s (允许的格式: %s)",
                    fileType.getDescription(),
                    String.join(", ", fileType.getAllowedExtensions())
            ));
        }
    }

    /**
     * 构建文件存储路径
     * 用户头像格式: avatars/user/{userId}_{timestamp}.{ext}
     * 智能体头像格式: avatars/agent/{agentId}_{timestamp}.{ext}
     * 知识库格式: knowledge/{knowledgeId}/{清理后的原始文件名}_{timestamp}.{ext}
     */
    public String buildFileKey(MultipartFile file, FileType fileType, String entityId) {
        StringBuilder keyBuilder = new StringBuilder();

        // 1. 添加基础路径
        keyBuilder.append(fileType.getBasePath());

        // 2. 如果是知识库，添加知识库ID作为文件夹
        if (fileType.needEntityFolder()) {
            keyBuilder.append("/").append(entityId);
        }

        // 3. 添加文件名
        keyBuilder.append("/");

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String nameWithoutExt = getNameWithoutExtension(originalFilename);

        // 生成时间戳版本号
        String timestamp = String.valueOf(System.currentTimeMillis());

        if (fileType.needEntityFolder()) {
            // 知识库：原始文件名 + 时间戳
            String cleanedName = cleanFileName(nameWithoutExt);
            keyBuilder.append(cleanedName).append("_").append(timestamp);
            if (!extension.isEmpty()) {
                keyBuilder.append(".").append(extension);
            }
        } else {
            // 头像：entityId + 时间戳
            keyBuilder.append(entityId).append("_").append(timestamp);
            if (!extension.isEmpty()) {
                keyBuilder.append(".").append(extension);
            }
        }

        return keyBuilder.toString();
    }

    /**
     * 获取文件名（不含扩展名）
     */
    private String getNameWithoutExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "file";
        }
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return fileName;
    }

    /**
     * 清理文件名：去除所有空格，移除特殊字符，保留中文、字母、数字、点、下划线、横线
     * @param fileName 原始文件名
     * @return 清理后的文件名
     */
    private String cleanFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unnamed";
        }

        // 分离文件名和扩展名
        String nameWithoutExt;
        String extension = "";

        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex > 0) {
            nameWithoutExt = fileName.substring(0, lastDotIndex);
            extension = fileName.substring(lastDotIndex);
        } else {
            nameWithoutExt = fileName;
        }

        // 清理文件名：去除所有空格，移除特殊字符
        String cleanedName = nameWithoutExt
                .replaceAll("\\s+", "")                        // 去除所有空格
                .replaceAll("[\\\\/:*?\"<>|]", "")              // 移除 Windows 非法字符
                .replaceAll("[\\[\\]{}()]", "")                 // 移除括号
                .replaceAll("[！@#￥%……&*（）]", "")            // 移除中文特殊字符
                .trim();                                         // 去除首尾空白

        // 如果清理后为空，使用默认名称
        if (cleanedName.isEmpty()) {
            cleanedName = "file";
        }

        return cleanedName + extension;
    }

    /**
     * 从URL中提取文件Key
     */
    private String extractKeyFromUrl(String fileUrl) {
        if (fileUrl.startsWith(cdnDomain)) {
            return fileUrl.substring(cdnDomain.length() + 1);
        }
        return fileUrl;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
