package com.mio.ai.framework.zagent.tools;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Locale;

/**
 * 图像模型预算预处理（zcode adapters/image jimp-compression 的对位移植，JDK ImageIO 实现）。
 * <p>预算与阶梯照搬 zcode contracts：输入上限 20MB、最长边 2000px、base64 上限 5MB、
 * token 预算 25000（base64 长度 × 0.125，即原始字节有效上限约 150KB）。
 * webp 无解码器按 zcode 透传（超预算报错）；gif 解码首帧、格式保留重编码；
 * PNG 保留一次原尺寸机会后单向转 JPEG；JPEG 质量阶梯 [80,60,40,20]；
 * 渐进缩放 [0.75,0.5,0.25]；兜底 JPEG 边长 [1000,800,600,400,300,200]。
 */
final class ImagePrepare {

    static final long MAX_INPUT_BYTES = 20L * 1024 * 1024;
    private static final int MAX_DIMENSION = 2000;
    private static final long MAX_BASE64_BYTES = 5L * 1024 * 1024;
    private static final long MAX_RAW_BYTES = (MAX_BASE64_BYTES * 3) / 4;
    private static final int MAX_TOKENS = 25_000;
    private static final double TOKEN_TO_BASE64_CHAR_RATIO = 0.125;

    private static final int[] JPEG_QUALITY_STEPS = {80, 60, 40, 20};
    private static final double[] PROGRESSIVE_SCALE_FACTORS = {0.75, 0.5, 0.25};
    private static final int[] AGGRESSIVE_JPEG_MAX_EDGES = {1000, 800, 600, 400, 300, 200};

    private ImagePrepare() {
    }

    record Prepared(byte[] data, String mimeType, boolean resized, boolean compressed,
                    String strategy, int originalWidth, int originalHeight,
                    int width, int height, long originalSize) {
    }

    /** 预处理失败（消息直接给模型，zcode ImageProcessorPortError 语义） */
    static class ImagePrepareException extends IllegalArgumentException {
        ImagePrepareException(String message) {
            super(message);
        }
    }

    static Prepared prepare(byte[] data, String declaredMime) {
        if (data.length == 0) {
            throw new ImagePrepareException("Image file is empty (0 bytes)");
        }
        String source = normalizeMime(declaredMime);
        if ("image/webp".equals(source)) {
            if (fitsBudget(data.length)) {
                return new Prepared(data, source, false, false, "original",
                        0, 0, 0, 0, data.length);
            }
            throw new ImagePrepareException("WebP image exceeds the model image budget and "
                    + "the current image adapter cannot transcode WebP");
        }

        BufferedImage image = decode(data);
        int ow = image.getWidth();
        int oh = image.getHeight();
        boolean withinDims = ow <= MAX_DIMENSION && oh <= MAX_DIMENSION;
        if (withinDims && fitsBudget(data.length)) {
            return new Prepared(data, source, false, false, "original",
                    ow, oh, ow, oh, data.length);
        }

        BufferedImage bounded = resizeToMaxEdge(image, MAX_DIMENSION);
        boolean boundedResized = bounded.getWidth() != ow || bounded.getHeight() != oh;
        if (withinDims) {
            Prepared candidate = formatPreserving(image, source, false);
            if (candidate != null) {
                return withOriginal(candidate, ow, oh, data.length);
            }
        }
        // PNG 只保留一次原尺寸无损优化机会；失败后单向转 JPEG（zcode 同规则）
        boolean preserveFormat = !"image/png".equals(source);
        if (preserveFormat && boundedResized) {
            Prepared candidate = formatPreserving(bounded, source, true);
            if (candidate != null) {
                return withOriginal(candidate, ow, oh, data.length);
            }
        }

        Prepared candidate = jpegLadder(bounded, boundedResized, "jpeg-quality");
        if (candidate != null) {
            return withOriginal(candidate, ow, oh, data.length);
        }
        for (double scale : PROGRESSIVE_SCALE_FACTORS) {
            int edge = Math.max(1, (int) Math.round(
                    Math.max(bounded.getWidth(), bounded.getHeight()) * scale));
            BufferedImage scaled = resizeToMaxEdge(bounded, edge);
            if (preserveFormat) {
                candidate = formatPreserving(scaled, source, true);
                if (candidate != null) {
                    return withOriginal(candidate, ow, oh, data.length);
                }
            }
            candidate = jpegLadder(scaled, true, "jpeg-quality");
            if (candidate != null) {
                return withOriginal(candidate, ow, oh, data.length);
            }
        }
        for (int edge : AGGRESSIVE_JPEG_MAX_EDGES) {
            BufferedImage scaled = resizeToMaxEdge(image, Math.min(edge, MAX_DIMENSION));
            candidate = encodeJpeg(scaled, 20, true, "jpeg-fallback");
            if (candidate != null && fitsBudget(candidate.data().length)) {
                return withOriginal(candidate, ow, oh, data.length);
            }
        }
        throw new ImagePrepareException("Unable to compress image (" + data.length
                + " bytes) within the requested model image budget");
    }

    /** 格式保留候选：png 重编码 / jpeg 质量阶梯 / gif 重编码（zcode findFormatPreservingCandidate） */
    private static Prepared formatPreserving(BufferedImage image, String mime, boolean resized) {
        switch (mime) {
            case "image/png": {
                byte[] bytes = encode(image, "png", 0);
                return bytes != null && fitsBudget(bytes.length)
                        ? new Prepared(bytes, mime, resized, true, "png-optimized",
                                image.getWidth(), image.getHeight(),
                                image.getWidth(), image.getHeight(), bytes.length)
                        : null;
            }
            case "image/jpeg":
                return jpegLadder(image, resized, resized ? "resized-jpeg-quality" : "jpeg-quality");
            case "image/gif": {
                byte[] bytes = encode(image, "gif", 0);
                return bytes != null && fitsBudget(bytes.length)
                        ? new Prepared(bytes, mime, resized, true, "preserve-format",
                                image.getWidth(), image.getHeight(),
                                image.getWidth(), image.getHeight(), bytes.length)
                        : null;
            }
            default:
                return null;
        }
    }

    /** JPEG 质量阶梯 [80,60,40,20]，首个入预算者胜出（zcode findJpegQualityCandidate） */
    private static Prepared jpegLadder(BufferedImage image, boolean resized, String strategy) {
        for (int quality : JPEG_QUALITY_STEPS) {
            Prepared candidate = encodeJpeg(image, quality, resized, strategy + "-q" + quality);
            if (candidate != null && fitsBudget(candidate.data().length)) {
                return candidate;
            }
        }
        return null;
    }

    private static Prepared encodeJpeg(BufferedImage image, int qualityPercent, boolean resized,
                                       String strategy) {
        BufferedImage flat = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flat.createGraphics();
        g.drawImage(image, 0, 0, Color.WHITE, null);
        g.dispose();
        byte[] bytes = encode(flat, "jpg", qualityPercent / 100.0f);
        return bytes == null ? null : new Prepared(bytes, "image/jpeg", resized, true, strategy,
                flat.getWidth(), flat.getHeight(), flat.getWidth(), flat.getHeight(), bytes.length);
    }

    /** 双三次插值缩放最长边至 maxEdge 内（zcode scaleToFit BICUBIC） */
    private static BufferedImage resizeToMaxEdge(BufferedImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxEdge) {
            return src;
        }
        double scale = maxEdge / (double) longest;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        boolean alpha = src.getColorModel().hasAlpha();
        BufferedImage out = new BufferedImage(nw, nh,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static BufferedImage decode(byte[] data) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                throw new ImagePrepareException("Unable to decode image data");
            }
            return image;
        } catch (ImagePrepareException e) {
            throw e;
        } catch (Exception e) {
            throw new ImagePrepareException("Unable to decode image data");
        }
    }

    private static byte[] encode(BufferedImage image, String format, float quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            return null;
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (quality > 0 && param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                if ("jpg".equals(format)) {
                    param.setCompressionType("JPEG");
                }
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
            stream.flush();
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            writer.dispose();
        }
    }

    /** zcode fitsImageBudget：raw 上限 + base64 上限 + token 预算三重约束 */
    private static boolean fitsBudget(long rawLength) {
        long base64Length = ((rawLength + 2) / 3) * 4;
        if (rawLength > MAX_RAW_BYTES || base64Length > MAX_BASE64_BYTES) {
            return false;
        }
        return Math.ceil(base64Length * TOKEN_TO_BASE64_CHAR_RATIO) <= MAX_TOKENS;
    }

    private static Prepared withOriginal(Prepared p, int ow, int oh, long originalSize) {
        return new Prepared(p.data(), p.mimeType(), p.resized(), p.compressed(), p.strategy(),
                ow, oh, p.width(), p.height(), originalSize);
    }

    private static String normalizeMime(String mime) {
        if (mime == null) {
            return "image/png";
        }
        String lower = mime.toLowerCase(Locale.ROOT);
        int semi = lower.indexOf(';');
        return semi >= 0 ? lower.substring(0, semi) : lower;
    }
}
