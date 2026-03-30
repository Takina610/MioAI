package com.mio.ai.superagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/3/29 10:49
 * @description: 文本加载器
 */
@Component
@Slf4j
public class DocumentReader {

    @Autowired
    ResourceLoader resourceLoader;

    public Resource[] getResource(String location) throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
        return resolver.getResources(location);
    }

    public List<Document> loadMarkdown(){
        List<Document> allDoc = new ArrayList<>();
        try {
            Resource[] resources = getResource("classpath:/file/*.md");
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(false)
                    .withIncludeBlockquote(false)
                    .build();
            //对每个resource进行处理
            for (Resource resource: resources){
                log.info("load file: " + resource.getFilename());
                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                allDoc.addAll(reader.get());
            }
        } catch (IOException e){
            log.error("文档加载失败, e:", e);
        }
        log.info("文档加载完成");
        return allDoc;
    }
}
