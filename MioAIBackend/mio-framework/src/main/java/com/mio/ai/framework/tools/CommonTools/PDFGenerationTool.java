package com.mio.ai.framework.tools.CommonTools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.mio.ai.common.common.FileType;
import com.mio.ai.common.utils.R2Util;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Component
public class PDFGenerationTool {

    private static final String ALLOWED_CHAR_REGEX = "^[a-zA-Z0-9_\\u4e00-\\u9fa5\\.]+$";

    @Autowired
    R2Util r2Util;

    @Tool(description = "根据内容生成PDF文件，必须全程使用标准简体中文，禁止英文、拼音、符号乱码，正常分段排版", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "PDF文件名，必须是中文、英文、数字组合") String fileName,
            @ToolParam(description = "要写入 PDF 的完整内容，必须是纯中文正常文本") String content) {

        if (!fileName.matches(ALLOWED_CHAR_REGEX)) {
            return "错误：文件名不合法，仅支持中文、英文、数字、下划线、.";
        }

        String fileDir = System.getProperty("java.io.tmpdir") + File.separator + "pdf";
        String localFilePath = fileDir + File.separator + fileName;

        try {
            FileUtil.mkdir(fileDir);

            try (PdfWriter writer = new PdfWriter(localFilePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {

                PdfFont font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H", PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                document.setFont(font);

                Paragraph paragraph = new Paragraph(content).setFontSize(12);
                document.add(paragraph);
            }

            String entityId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String fileUrl = r2Util.uploadLocalFile(localFilePath, FileType.PDF_FILE, entityId);

            FileUtil.del(localFilePath);

            return "PDF生成成功！文件链接：" + fileUrl;
        } catch (IOException e) {
            return "PDF生成失败：" + e.getMessage();
        }
    }
}

