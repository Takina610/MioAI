package com.mio.ai.customagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/5 21:07
 * @description:
 */

@Component
@Slf4j
public class PdfReader {
    public List<Document> loadPdf() {

        List<Document> allDoc = new ArrayList<>();

        try {
            //对每个resource进行处理
            for (Resource resource: resources){
                log.info("load file: " + resource.getFilename());
                PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource,
                PdfDocumentReaderConfig.builder()
                        .withPageTopMargin(0)
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                .withNumberOfTopTextLinesToDelete(0)
                                .build())
                        .withPagesPerDocument(1)
                        .build());
                allDoc.addAll(pdfReader.get());
            }
        } catch (IOException e){
            log.error("文档加载失败, e:", e);
        }
        log.info("文档加载完成");
        return allDoc;
    }
}
