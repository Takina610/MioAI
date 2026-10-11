package com.mio.ai.framework.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/5 21:02
 * @description: 纯文本阅读器。UTF-8 严格解码失败时回退 GBK（Windows 中文环境常见编码），
 *               避免旧编码文件被读成乱码后向量化出垃圾数据。
 */
@Component
@Slf4j
public class TxtReader {

    public List<Document> loadTxt(File file) {
        Path path = file.toPath();
        String content = readWithFallback(path);
        if (content == null || content.isBlank()) {
            log.warn("文本文件为空或无法解码: {}", file.getName());
            return Collections.emptyList();
        }
        return List.of(new Document(content));
    }

    private String readWithFallback(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            try {
                log.info("文件非 UTF-8 编码，回退 GBK 解码: {}", path.getFileName());
                return Files.readString(path, Charset.forName("GBK"));
            } catch (Exception gbkError) {
                log.error("GBK 解码也失败: {}", path.getFileName(), gbkError);
                return null;
            }
        } catch (Exception e) {
            log.error("读取文本文件失败: {}", path.getFileName(), e);
            return null;
        }
    }
}
