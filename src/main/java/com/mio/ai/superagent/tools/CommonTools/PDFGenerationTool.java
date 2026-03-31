package com.mio.ai.superagent.tools.CommonTools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.mio.ai.common.constant.SystemConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/**
 * @author: Takina
 * @date: 2026/3/31 18:43
 * @description: PDF 生成工具
 */

@Component
public class PDFGenerationTool {

    private static final String ALLOWED_CHAR_REGEX = "^[a-zA-Z0-9_\\u4e00-\\u9fa5\\.]+$";

    // 👇 彻底锁死中文输出
    @Tool(description = "根据内容生成PDF文件，必须全程使用标准简体中文，禁止英文、拼音、符号乱码，正常分段排版", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "PDF文件名，必须是中文、英文、数字组合，例如：宜春约会计划.pdf") String fileName,
            @ToolParam(description = "要写入PDF的完整内容，必须是纯中文正常文本") String content) {

        // 文件名安全校验
        if (!fileName.matches(ALLOWED_CHAR_REGEX)) {
            return "错误：文件名不合法，仅支持中文、英文、数字、下划线、.";
        }

        String fileDir = SystemConstant.FILE_SAVE_DIR + File.separator + "pdf";
        String filePath = fileDir + File.separator + fileName;

        try {
            FileUtil.mkdir(fileDir);

            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {

                PdfFont font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H", PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                document.setFont(font);

                // 自动按原格式换行输出
                Paragraph paragraph = new Paragraph(content).setFontSize(12);
                document.add(paragraph);
            }

            return "PDF生成成功！路径：" + filePath;
        } catch (IOException e) {
            return "PDF生成失败：" + e.getMessage();
        }
    }
}

