package com.mio.ai.customagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/5 21:02
 * @description:
 */

@Component
@Slf4j
public class TxtReader {
    public List<Document> loadTxt(File file) {
        try {
            TextReader reader = new TextReader(new FileSystemResource(file));
            return reader.get();
        } catch (Exception e) {
            log.error("读取文本文件失败", e);
            return Collections.emptyList();
        }
    }
}
