package com.mio.ai.customagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/5 21:11
 * @description:
 */

@Component
@Slf4j
public class TikaReader {
    public List<Document> loadTika() {
        List<Document> allDoc = new ArrayList<>();

        try {
            //对每个resource进行处理
            for (Resource resource: resources){
                log.info("load file: " + resource.getFilename());
                TikaDocumentReader reader = new TikaDocumentReader(resource);
                allDoc.addAll(reader.get());
            }
        } catch (IOException e){
            log.error("文档加载失败, e:", e);
        }
        log.info("文档加载完成");
        return allDoc;
    }
}
