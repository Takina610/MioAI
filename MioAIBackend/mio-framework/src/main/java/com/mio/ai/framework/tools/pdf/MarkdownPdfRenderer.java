package com.mio.ai.framework.tools.pdf;

import cn.hutool.http.HttpUtil;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.font.FontProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 子集 → 结构化 PDF 渲染。
 * 字体：Noto Sans CJK SC（中文/符号）+ Noto Emoji（emoji 表情）按字符自动回退；
 * 行级支持标题/无序有序列表（缩进嵌套）/表格/引用/分隔线/整行图片；
 * 行内支持 **加粗**、`代码`、~~删除线~~、[链接](url)（渲染为带下划线文本）。
 */
public final class MarkdownPdfRenderer {

    private static final String CJK_FONT = "/fonts/NotoSansCJKsc-Regular.otf";
    private static final String EMOJI_FONT = "/fonts/NotoEmoji.ttf";
    private static final float BODY_SIZE = 11.5f;
    private static final int MAX_IMAGES = 5;
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

    private static final Pattern INLINE = Pattern.compile(
            "\\*\\*(.+?)\\*\\*|`([^`\\n]+)`|~~(.+?)~~|\\[([^\\]\\n]+)\\]\\([^)\\s]+\\)");
    private static final Pattern IMAGE_LINE = Pattern.compile("^\\s*!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)\\s*$");
    private static final Pattern TABLE_SEP = Pattern.compile("^:?-{2,}:?$");

    private MarkdownPdfRenderer() {
    }

    /** 是否具备富排版字体（缺失时调用方走 STSong 纯文本降级路径） */
    public static boolean fontsAvailable() {
        return MarkdownPdfRenderer.class.getResource(CJK_FONT) != null
                && MarkdownPdfRenderer.class.getResource(EMOJI_FONT) != null;
    }

    public static void render(String markdown, String path) throws IOException {
        markdown = preprocessHtml(markdown);
        FontProvider provider = new FontProvider();
        provider.addFont(readResource(CJK_FONT));
        provider.addFont(readResource(EMOJI_FONT));

        try (PdfWriter writer = new PdfWriter(path);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {
            document.setFontProvider(provider);
            document.setFontFamily("Noto Sans CJK SC");
            document.setMargins(40, 40, 40, 40);
            renderBlocks(markdown, document);
        }
    }

    /** 行级块解析（表格连续行聚合） */
    private static void renderBlocks(String markdown, Document document) {
        String[] lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        int i = 0;
        int imageCount = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                i++;
                continue;
            }
            // 表格：连续 | 开头的行聚合（含 |---| 分隔行跳过）
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length() > 2) {
                int j = i;
                List<String[]> rows = new ArrayList<>();
                while (j < lines.length) {
                    String t = lines[j].trim();
                    if (!(t.startsWith("|") && t.endsWith("|") && t.length() > 2)) {
                        break;
                    }
                    rows.add(splitTableRow(t.substring(1, t.length() - 1)));
                    j++;
                }
                if (rows.size() >= 1) {
                    document.add(buildTable(rows));
                    document.add(spacer(6));
                }
                i = j;
                continue;
            }
            // 整行图片
            Matcher img = IMAGE_LINE.matcher(line);
            if (img.matches()) {
                if (imageCount < MAX_IMAGES) {
                    imageCount++;
                    addImage(document, img.group(2), img.group(1));
                }
                i++;
                continue;
            }
            // 分隔线
            if (trimmed.matches("^([-*_])\\1{2,}$")) {
                document.add(new LineSeparator(new SolidLine(0.6f)).setMarginTop(6).setMarginBottom(8));
                i++;
                continue;
            }
            // 引用
            if (trimmed.startsWith("> ")) {
                document.add(inline(trimmed.substring(2))
                        .setFontSize(BODY_SIZE - 1)
                        .setFontColor(new DeviceGray(0.35f))
                        .setBorderLeft(new SolidBorder(new DeviceGray(0.7f), 2))
                        .setPaddingLeft(8)
                        .setMarginTop(4).setMarginBottom(4));
                i++;
                continue;
            }
            // 标题
            int level = headingLevel(trimmed);
            if (level > 0) {
                float size = level == 1 ? 18 : level == 2 ? 15 : level == 3 ? 13 : BODY_SIZE;
                document.add(inline(trimmed.substring(level + 1))
                        .setFontSize(size)
                        .setMarginTop(level <= 2 ? 12 : 8)
                        .setMarginBottom(6));
                i++;
                continue;
            }
            // 列表（缩进两空格一级）
            String bullet = bulletMarker(trimmed);
            if (bullet != null || trimmed.matches("^\\d+\\.\\s+.*")) {
                int indent = indentSpaces(line);
                String content = bullet != null
                        ? trimmed.substring(bullet.length())
                        : trimmed.replaceFirst("^\\d+\\.\\s+", "");
                String mark = bullet != null ? "•  " : trimmed.split("\\.")[0] + ".  ";
                Paragraph item = new Paragraph()
                        .setFontSize(BODY_SIZE)
                        .setMarginLeft(14 + (long) indent / 2 * 16)
                        .setMarginTop(2)
                        .add(new Text(mark));
                appendInlineRuns(item, content);
                document.add(item);
                i++;
                continue;
            }
            // 普通段落
            document.add(inline(trimmed).setFontSize(BODY_SIZE).setMarginTop(3).setMarginBottom(3));
            i++;
        }
    }

    private static Table buildTable(List<String[]> rows) {
        rows.removeIf(MarkdownPdfRenderer::isSeparatorRow);
        int cols = rows.stream().mapToInt(r -> r.length).max().orElse(1);
        Table table = new Table(UnitValue.createPercentArray(cols)).useAllAvailableWidth().setMarginTop(4);
        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            boolean header = r == 0;
            for (int c = 0; c < cols; c++) {
                String cell = c < row.length ? row[c].trim() : "";
                Paragraph p = inline(cell).setFontSize(10);
                Cell tableCell = new Cell().add(p).setPadding(4);
                if (header) {
                    tableCell.setBackgroundColor(new DeviceGray(0.92f));
                }
                table.addCell(tableCell);
            }
        }
        return table;
    }

    private static boolean isSeparatorRow(String[] cells) {
        if (cells.length == 0) {
            return false;
        }
        for (String cell : cells) {
            if (!TABLE_SEP.matcher(cell.trim()).matches()) {
                return false;
            }
        }
        return true;
    }

    private static String[] splitTableRow(String body) {
        return body.split("\\|", -1);
    }

    /** 整行图片：下载并按页宽缩放嵌入，失败时留占位说明 */
    private static void addImage(Document document, String url, String alt) {
        try {
            byte[] bytes = HttpUtil.createGet(url).timeout(8000).execute().bodyBytes();
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                throw new IOException("图片为空或过大");
            }
            Image image = new Image(ImageDataFactory.create(bytes));
            float maxWidth = document.getPdfDocument().getDefaultPageSize().getWidth() - 80;
            if (image.getImageWidth() > maxWidth) {
                image.scaleToFit(maxWidth, 420);
            }
            document.add(image.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER)
                    .setMarginTop(6).setMarginBottom(6));
        } catch (Exception e) {
            document.add(new Paragraph("[图片未能加载" + (alt == null || alt.isEmpty() ? "" : "：" + alt) + "]")
                    .setFontSize(9).setFontColor(new DeviceGray(0.55f)).setMarginTop(2).setMarginBottom(2));
        }
    }

    /** 行内解析：加粗（描边模拟）/代码/删除线/链接（下划线文本） */
    private static Paragraph inline(String text) {
        Paragraph p = new Paragraph();
        appendInlineRuns(p, text);
        return p;
    }

    private static void appendInlineRuns(Paragraph p, String text) {
        Matcher m = INLINE.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) {
                p.add(new Text(text.substring(last, m.start())));
            }
            if (m.group(1) != null) {
                p.add(new Text(m.group(1)).setTextRenderingMode(PdfCanvasConstants.TextRenderingMode.FILL_STROKE)
                        .setStrokeWidth(0.25f));
            } else if (m.group(2) != null) {
                p.add(new Text(m.group(2)).setFontSize(BODY_SIZE - 1)
                        .setFontColor(new DeviceGray(0.15f))
                        .setBackgroundColor(new DeviceGray(0.93f)));
            } else if (m.group(3) != null) {
                p.add(new Text(m.group(3)).setLineThrough());
            } else {
                p.add(new Text(m.group(4)).setUnderline().setFontColor(ColorConstants.BLUE));
            }
            last = m.end();
        }
        if (last < text.length()) {
            p.add(new Text(text.substring(last)));
        }
    }

    private static int headingLevel(String trimmed) {
        int level = 0;
        while (level < 6 && level < trimmed.length() && trimmed.charAt(level) == '#') {
            level++;
        }
        if (level > 0 && level < trimmed.length() && trimmed.charAt(level) == ' ') {
            return level;
        }
        return 0;
    }

    private static String bulletMarker(String trimmed) {
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            return trimmed.substring(0, 2);
        }
        if (trimmed.startsWith("• ")) {
            return trimmed.substring(0, 2);
        }
        return null;
    }

    private static int indentSpaces(String rawLine) {
        int n = 0;
        while (n < rawLine.length() && rawLine.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    private static Paragraph spacer(float height) {
        return new Paragraph("").setMarginBottom(height);
    }

    /**
     * HTML 标签与实体预处理：模型常直接输出 <br>、&emsp;、<b> 等——
     * 先把常见 HTML 标签转成等价 markdown/换行、剥除未知标签，
     * 最后再解码实体（保证 &lt;br&gt; 之类的转义文本不被当标签处理）。
     */
    private static String preprocessHtml(String md) {
        String s = md.replace("\n", "\n").replace("\r", "\n");
        s = s.replaceAll("(?i)<br\s*/?>", "\n");
        s = s.replaceAll("(?i)<hr\s*/?>", "\n---\n");
        s = s.replaceAll("(?i)<h1(\s[^>]*)?>", "\n# ");
        s = s.replaceAll("(?i)<h2(\s[^>]*)?>", "\n## ");
        s = s.replaceAll("(?i)<h3(\s[^>]*)?>", "\n### ");
        s = s.replaceAll("(?i)<h[4-6](\s[^>]*)?>", "\n#### ");
        s = s.replaceAll("(?i)<li(\s[^>]*)?>", "- ");
        s = s.replaceAll("(?i)</(p|div|h[1-6]|li|ul|ol|tr|table|blockquote)>", "\n");
        s = s.replaceAll("(?i)<(b|strong)>(.+?)</\\1>", "**$2**");
        s = s.replaceAll("(?i)<(i|em|u)>(.+?)</\\1>", "$2");
        s = s.replaceAll("(?i)<(code)>(.+?)</\\1>", "`$2`");
        s = s.replaceAll("(?i)<sub>(.+?)</sub>", "$1");
        s = s.replaceAll("(?i)<sup>(.+?)</sup>", "$1");
        s = s.replaceAll("(?i)<[a-zA-Z][^>\n]{0,200}/?>", "");
        s = s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&ensp;", " ")
                .replace("&emsp;", "  ")
                .replace("&hellip;", "…")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&ldquo;", "“")
                .replace("&rdquo;", "”")
                .replace("&lsquo;", "‘")
                .replace("&rsquo;", "’")
                .replace("&middot;", "·")
                .replace("&times;", "×")
                .replace("&deg;", "°");
        return s;
    }

    private static byte[] readResource(String path) throws IOException {
        try (var in = MarkdownPdfRenderer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("字体缺失: " + path);
            }
            return in.readAllBytes();
        }
    }
}
