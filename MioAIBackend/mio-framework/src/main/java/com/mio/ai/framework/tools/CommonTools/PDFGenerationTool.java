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
import com.mio.ai.framework.tools.pdf.MarkdownPdfRenderer;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * @author: Takina
 * @date: 2026/3/31 17:06
 * @description: PDF 文档生成工具：markdown 富排版渲染（标题/列表/表格/加粗/引用/emoji/图片），
 * 字体缺失时降级 STSong 纯文本，字形异常时白名单兜底重渲，上传 R2 后回传同源代理链接。
 */
@Component
public class PDFGenerationTool {

    private static final String FILE_NAME_ILLEGAL = "[\\:*?\"<>|]";

    /** 渲染兜底白名单之外的全部剔除（仅保留常用中英文与中英文标点） */
    private static final String STRICT_KEEP =
            "[^\\u4e00-\\u9fa5\\u3400-\\u4dbfA-Za-z0-9 \\t\\n，。、；：！？（）()《》【】“”‘'\"\\[\\]{}%+*/=.,;:!?#@$&~…—·-]";

    @Autowired
    R2Util r2Util;

    @Tool(description = "把完整内容排版为 PDF 并上传，返回用户可直接打开下载的链接。"
            + "排版支持（建议充分利用，让文档丰富美观）：#/##/### 多级标题；- 与 1. 列表（行首缩进两空格嵌套）；"
            + "| 表格 |；**加粗**；`代码`；~~删除线~~；> 引用；--- 分隔线；emoji 表情；![说明](图片网址) 插图。"
            + "直接传最终 markdown 内容即可，不要先写中间文件", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "PDF文件名（不带 .pdf 后缀），如：上海3日旅游计划") String fileName,
            @ToolParam(description = "完整文档内容") String content) {

        String safeName = sanitizeFileName(fileName);
        if (safeName.isBlank()) {
            return "错误：文件名为空";
        }
        if (content == null || content.isBlank()) {
            return "错误：内容为空";
        }

        String fileDir = System.getProperty("java.io.tmpdir") + File.separator + "pdf";
        String localFilePath = fileDir + File.separator + safeName + ".pdf";
        FileUtil.mkdir(fileDir);

        String cleaned = sanitizeControlChars(content);
        if (cleaned.isBlank()) {
            return "错误：内容清理后为空，请提供有效文本";
        }
        try {
            // 主路径：Noto CJK + Emoji 字体，markdown 富排版（emoji/加粗/表格/图片等）
            MarkdownPdfRenderer.render(cleaned, localFilePath);
        } catch (Exception e) {
            // 字体缺失或渲染异常：降级 STSong 纯文本（清 emoji），保证一定出文档
            String legacy = sanitizeContent(cleaned);
            if (legacy.isBlank()) {
                return "PDF生成失败：" + e.getMessage();
            }
            try {
                renderPlain(legacy, localFilePath);
            } catch (Exception e2) {
                String strict = legacy.replaceAll(STRICT_KEEP, "");
                if (strict.isBlank()) {
                    return "PDF生成失败：" + e2.getMessage();
                }
                try {
                    renderPlain(strict, localFilePath);
                } catch (Exception e3) {
                    return "PDF生成失败：" + e3.getMessage();
                }
            }
        }

        try {
            String entityId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String fileUrl = r2Util.uploadLocalFile(localFilePath, FileType.PDF_FILE, entityId);
            // 回传同源代理链接（相对路径）：聊天/分享页点击直接预览或 ?download=1 下载，
            // 不经 CDN 自定义域名，规避 DNS/代理/防盗链等环境限制
            String objectName = fileUrl.substring(fileUrl.lastIndexOf('/') + 1);
            return "PDF生成成功！下载链接：/api/file/pdf/" + objectName;
        } catch (Exception e) {
            return "PDF上传失败：" + e.getMessage();
        } finally {
            FileUtil.del(localFilePath);
        }
    }

    /** 降级渲染：STSong 纯文本逐行（富排版字体不可用时兜底） */
    private void renderPlain(String content, String path) throws IOException {
        try (PdfWriter writer = new PdfWriter(path);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {

            PdfFont font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H",
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            document.setFont(font);
            document.setMargins(36, 36, 36, 36);

            for (String line : content.split("\n", -1)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    document.add(new Paragraph("").setMarginBottom(4));
                } else if (trimmed.startsWith("### ")) {
                    document.add(new Paragraph(trimmed.substring(4)).setFontSize(13).setMarginTop(8));
                } else if (trimmed.startsWith("## ")) {
                    document.add(new Paragraph(trimmed.substring(3)).setFontSize(15).setMarginTop(10));
                } else if (trimmed.startsWith("# ")) {
                    document.add(new Paragraph(trimmed.substring(2)).setFontSize(18)
                            .setMarginTop(12).setMarginBottom(6));
                } else if (trimmed.startsWith("- ") || trimmed.startsWith("• ")) {
                    document.add(new Paragraph("•  " + trimmed.substring(2)).setFontSize(12).setMarginLeft(16));
                } else {
                    document.add(new Paragraph(trimmed).setFontSize(12));
                }
            }
        }
    }

    /** 仅清理控制字符（保留 emoji 与全部可见字符） */
    private static String sanitizeControlChars(String content) {
        StringBuilder sb = new StringBuilder(content.length());
        content.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r' || cp == '\t' || cp >= 0x20) {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString();
    }


    /** 清理非 BMP 字符（emoji 等 STSong 无法编码）与控制字符，保留换行/制表 */
    private static String sanitizeContent(String content) {
        StringBuilder sb = new StringBuilder(content.length());
        content.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r' || cp == '\t') {
                sb.append((char) cp);
            } else if (cp <= 0xFFFF && cp >= 0x20) {
                sb.append((char) cp);
            }
            // 非 BMP（emoji/增补平面）与控制字符直接丢弃
        });
        return sb.toString();
    }

    /** 文件名归一化：取末段、清理非法字符，兼容模型带了 .pdf 后缀的情况 */
    private static String sanitizeFileName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = name.replace("\\", "/");
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        normalized = normalized.replaceAll(FILE_NAME_ILLEGAL, "_").trim();
        if (normalized.toLowerCase().endsWith(".pdf")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
    }
}
