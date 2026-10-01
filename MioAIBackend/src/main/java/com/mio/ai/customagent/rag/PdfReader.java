package com.mio.ai.customagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * @author: Takina
 * @date: 2026/4/5 21:07
 * @description:
 */

@Component
@Slf4j
public class PdfReader {
    public List<Document> loadPdf(FileSystemResource resource) {
        try {
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource,
                    PdfDocumentReaderConfig.builder()
                            .withPageTopMargin(0)
                            .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                    .withNumberOfTopTextLinesToDelete(0)
                                    .build())
                            .withPagesPerDocument(1)
                            .build());
            return pdfReader.get();
        } catch (Exception e) {
            log.error("读取PDF文件失败", e);
            return Collections.emptyList();
        }
    }
}
