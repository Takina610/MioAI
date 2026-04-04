package com.mio.ai.common.utils;

import com.mio.ai.common.common.FileType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Slf4j
class R2UtilTest {

    @Autowired
    private R2Util r2UploadUtil;

    @Test
    void uploadFile() throws IOException {
        // 读取 rag 目录下的 md 文件
        ClassPathResource resource = new ClassPathResource("0.png");

        try (InputStream inputStream = resource.getInputStream()) {
            // 转换为 MultipartFile
            MultipartFile file = new MockMultipartFile(
                    "file",
                    "0.png",
                    "image/png",
                    inputStream
            );

            String fileUrl = r2UploadUtil.uploadFile(file, FileType.AGENT_AVATAR, "0");

            System.out.println("上传成功: " + fileUrl);
        }
    }

    @Test
    void deleteFile() throws IOException {
        // 先上传一个文件
        ClassPathResource resource = new ClassPathResource("rag/CS 比赛数据检索和分析 - 地图打法篇.md");

        try (InputStream inputStream = resource.getInputStream()) {
            MultipartFile file = new MockMultipartFile(
                    "file",
                    "CS 比赛数据检索和分析 - 地图打法篇.md",
                    "text/markdown",
                    inputStream
            );

            String knowledgeId = "test_delete_001";
            String fileUrl = r2UploadUtil.uploadFile(file, FileType.KNOWLEDGE_FILE, knowledgeId);

            // 删除文件
            r2UploadUtil.deleteFile(fileUrl);

            // 验证文件已删除
            String fileKey = fileUrl.replace("https://cdn.tak1na.cn/", "");
            boolean exists = r2UploadUtil.fileExists(fileKey);
            assertThat(exists).isFalse();

            System.out.println("删除成功: " + fileUrl);
        }
    }

    @Test
    void fileExists() throws IOException {
        // 先上传一个文件
        ClassPathResource resource = new ClassPathResource("rag/CS 比赛数据检索和分析 - 地图打法篇.md");

        try (InputStream inputStream = resource.getInputStream()) {
            MultipartFile file = new MockMultipartFile(
                    "file",
                    "CS 比赛数据检索和分析 - 地图打法篇.md",
                    "text/markdown",
                    inputStream
            );

            String knowledgeId = "test_exists_001";
            String fileUrl = r2UploadUtil.uploadFile(file, FileType.KNOWLEDGE_FILE, knowledgeId);

            // 检查文件是否存在
            String fileKey = fileUrl.replace("https://cdn.tak1na.cn/", "");
            boolean exists = r2UploadUtil.fileExists(fileKey);

            assertThat(exists).isTrue();
            System.out.println("文件存在: " + fileUrl);

            // 清理
            r2UploadUtil.deleteFile(fileUrl);
        }
    }

    @Test
    void buildFileKey() throws IOException {
        // 读取测试文件
        ClassPathResource resource = new ClassPathResource("rag/CS 比赛数据检索和分析 - 地图打法篇.md");

        try (InputStream inputStream = resource.getInputStream()) {
            MultipartFile file = new MockMultipartFile(
                    "file",
                    "CS 比赛数据检索和分析 - 地图打法篇.md",
                    "text/markdown",
                    inputStream
            );
            String knowledgeId = "test_key_001";

            // 上传文件获取实际的 fileKey
            String fileUrl = r2UploadUtil.buildFileKey(file, FileType.KNOWLEDGE_FILE, knowledgeId);
            log.info("file Url: {}", fileUrl);
            String actualFileKey = fileUrl.replace("https://cdn.tak1na.cn/", "");
            log.info("actualFileKey: {}", actualFileKey);
        }
    }
}