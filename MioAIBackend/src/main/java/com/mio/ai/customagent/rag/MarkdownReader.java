package com.mio.ai.customagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/5 20:58
 * @description:
 */

@Component
@Slf4j
public class MarkdownReader {

    public List<Document> loadMarkdown(FileSystemResource resource){
        try {
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(false)
                    .withIncludeBlockquote(false)
                    .build();
            MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
            return reader.get();
        } catch (Exception e) {
            log.error("读取Markdown文件失败", e);
            return Collections.emptyList();
        }
    }
}
