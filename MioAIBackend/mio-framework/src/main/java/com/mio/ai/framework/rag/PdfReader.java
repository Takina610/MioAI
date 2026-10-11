package com.mio.ai.framework.rag;

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
 * @description: PDF 阅读器。整本 PDF 合并为一个 Document 再交给分割器：
 *               逐页切分会在每页边界硬性断句（句子跨页被切断、页眉页脚碎片化），
 *               合并后由分割器按 token+重叠切分，跨页句子可由重叠区兜住。
 *               已知局限：PDFBox 对表格/多栏只能线性化文本，结构信息会丢失。
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
                            // 0 = 全部页合并为一个 Document
                            .withPagesPerDocument(0)
                            .build());
            return pdfReader.get();
        } catch (Exception e) {
            log.error("读取PDF文件失败", e);
            return Collections.emptyList();
        }
    }
}
