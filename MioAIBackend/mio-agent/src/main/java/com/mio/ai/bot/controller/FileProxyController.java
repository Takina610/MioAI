package com.mio.ai.bot.controller;

import com.mio.ai.common.utils.R2Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * PDF 同源代理：聊天中的 PDF 链接不再直连 CDN 自定义域名，
 * 而是经后端从 R2 读取后同源转发——不受本机 DNS/代理劫持、CDN 防盗链、
 * referrer 策略等任何限制，点开即预览，?download=1 触发下载。
 */
@RestController
@RequestMapping("/file")
public class FileProxyController {

    /** pdf_files/ 下由工具生成的对象名：随机id_时间戳.pdf */
    private static final Pattern PDF_NAME = Pattern.compile("^[A-Za-z0-9_\\-]+\\.pdf$");

    @Autowired
    private R2Util r2Util;

    @GetMapping("/pdf/{fileName}")
    public ResponseEntity<byte[]> pdf(@PathVariable String fileName,
                                      @RequestParam(required = false) String download) {
        if (fileName == null || !PDF_NAME.matcher(fileName).matches()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            byte[] bytes = r2Util.downloadFile("pdf_files/" + fileName);
            String disposition = "1".equals(download)
                    ? "attachment; filename=" + fileName
                    : "inline; filename=" + fileName;
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                    .contentLength(bytes.length)
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
