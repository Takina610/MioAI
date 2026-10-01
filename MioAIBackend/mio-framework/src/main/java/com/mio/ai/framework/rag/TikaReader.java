package com.mio.ai.framework.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/5 21:11
 * @description:
 */

@Component
@Slf4j
public class TikaReader {
    public List<Document> loadTika(FileSystemResource resource) {
        try {
            TikaDocumentReader reader = new TikaDocumentReader(resource);
            return reader.get();
        } catch (Exception e) {
            log.error("读取Tika文件失败", e);
            return Collections.emptyList();
        }
    }
}
