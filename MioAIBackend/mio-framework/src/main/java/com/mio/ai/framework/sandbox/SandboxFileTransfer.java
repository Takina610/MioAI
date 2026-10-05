package com.mio.ai.framework.sandbox;

import cn.hutool.core.util.StrUtil;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 沙箱文件传输：用户附件经后端 SFTP 落到沙箱工作区（uploads/&lt;chatId&gt;/），
 * Agent 产出经 outputs/&lt;chatId&gt;/ 由下载接口拉回给用户——文件与 Agent 的
 * 文件工具同处一个文件系统，读写零搬运。
 * <p>路径一律为工作区内相对路径；下载/列举只放行 uploads|outputs 两个暂存根。
 */
@Slf4j
@Component
public class SandboxFileTransfer {

    /** 附件暂存根（工作区内相对目录）：输入与输出各一个 */
    public static final String UPLOADS_ROOT = "uploads";
    public static final String OUTPUTS_ROOT = "outputs";

    private final ObjectProvider<SandboxSession> sessionProvider;
    private final SandboxProperties props;

    public SandboxFileTransfer(ObjectProvider<SandboxSession> sessionProvider, SandboxProperties props) {
        this.sessionProvider = sessionProvider;
        this.props = props;
    }

    /** 沙箱未启用时传输能力整体不可用（附件接口与 Agent 附件提示随之关闭） */
    public boolean available() {
        SandboxSession session = sessionProvider.getIfAvailable();
        return session != null && props.isEnabled();
    }

    /**
     * 上传用户附件：落到 uploads/&lt;chatId&gt;/&lt;8位随机_安全文件名&gt;，返回工作区内相对路径。
     * 文件名保留可读性（Agent 和用户都要认），随机前缀防同名覆盖。
     */
    public String upload(String chatId, String originalName, InputStream in) {
        String safeName = random8() + "_" + sanitizeName(originalName);
        String dir = UPLOADS_ROOT + "/" + safeDir(chatId);
        withSftp(sftp -> {
            mkdirs(sftp, dir);
            sftp.put(in, dir + "/" + safeName, ChannelSftp.OVERWRITE);
            return null;
        });
        return dir + "/" + safeName;
    }

    /** 下载暂存文件到输出流（只放行 uploads/outputs 根内路径，调用方先做路径校验） */
    public void downloadTo(String relative, OutputStream out) {
        withSftp(sftp -> {
            sftp.get(relative, out);
            return null;
        });
    }

    /** 列目录（不存在返回空列表）：[{path, name, size}]，size 为字节 */
    public List<Map<String, Object>> listDir(String relativeDir) {
        return withSftp(sftp -> {
            List<Map<String, Object>> items = new ArrayList<>();
            try {
                for (Object entry : sftp.ls(relativeDir)) {
                    if (!(entry instanceof ChannelSftp.LsEntry file) || file.getFilename().startsWith(".")) {
                        continue;
                    }
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("path", relativeDir + "/" + file.getFilename());
                    item.put("name", file.getFilename());
                    item.put("size", file.getAttrs().getSize());
                    items.add(item);
                }
            } catch (SftpException e) {
                if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    throw e;
                }
            }
            return items;
        });
    }

    /** 确保 outputs/&lt;chatId&gt;/ 存在（Agent 产出交付目录，轮次开始时创建） */
    public void ensureOutputDir(String chatId) {
        withSftp(sftp -> {
            mkdirs(sftp, OUTPUTS_ROOT + "/" + safeDir(chatId));
            return null;
        });
    }

    /** 列出目录中不在 beforeNames 里的新文件（轮次结束收集 Agent 产出） */
    public List<Map<String, Object>> listNew(String relativeDir, java.util.Set<String> beforeNames) {
        List<Map<String, Object>> items = listDir(relativeDir);
        items.removeIf(item -> beforeNames.contains(String.valueOf(item.get("name"))));
        return items;
    }

    /** 删除暂存文件（用户点 X 撤回刚上传的附件；文件不存在视作已删除） */
    public void delete(String relative) {
        withSftp(sftp -> {
            try {
                sftp.rm(relative);
            } catch (SftpException e) {
                if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    throw e;
                }
            }
            return null;
        });
    }

    /** 清理超过 keepDays 的暂存文件（uploads/outputs 全量，定时任务调用） */
    public void cleanupOld(int keepDays) {
        SandboxSession session = sessionProvider.getIfAvailable();
        if (session == null) {
            return;
        }
        session.exec("find " + UPLOADS_ROOT + " " + OUTPUTS_ROOT
                + " -type f -mtime +" + keepDays + " -delete 2>/dev/null; "
                + "find " + UPLOADS_ROOT + " " + OUTPUTS_ROOT
                + " -mindepth 1 -type d -empty -delete 2>/dev/null; true", null);
    }

    // ---------- 内部工具 ----------

    private <T> T withSftp(SandboxSession.SftpOp<T> op) {
        SandboxSession session = sessionProvider.getIfAvailable();
        if (session == null) {
            throw new IllegalStateException("沙箱未启用，文件传输不可用");
        }
        try {
            return session.withSftp(sftp -> {
                cdWorkdir(sftp);
                return op.apply(sftp);
            });
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("沙箱文件传输失败: {}", e.getMessage());
            throw new IllegalStateException("沙箱文件传输失败: " + e.getMessage(), e);
        }
    }

    /** cd 到工作目录（不存在则创建），之后的相对路径操作都落在工作区内 */
    private void cdWorkdir(ChannelSftp sftp) throws Exception {
        String workdir = StrUtil.blankToDefault(props.getWorkdir(), "sandbox").replaceFirst("^~/?", "");
        if (workdir.isEmpty()) {
            return;
        }
        try {
            sftp.cd(workdir);
        } catch (SftpException e) {
            mkdirs(sftp, workdir);
            sftp.cd(workdir);
        }
    }

    /** 逐级 mkdir（SFTP 无 mkdir -p），已存在的层级跳过 */
    private void mkdirs(ChannelSftp sftp, String relativeDir) throws SftpException {
        String current = "";
        for (String segment : relativeDir.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            current = current.isEmpty() ? segment : current + "/" + segment;
            if (isDirectory(sftp, current)) {
                continue;
            }
            sftp.mkdir(current);
        }
    }

    private boolean isDirectory(ChannelSftp sftp, String path) {
        try {
            return sftp.stat(path).isDir();
        } catch (SftpException e) {
            return false;
        }
    }

    private String random8() {
        // 同毫秒批量上传会撞前缀（UUID 保证唯一）
        return java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /** 文件名净化：去路径分隔/控制字符，保中文名，限长 80 */
    static String sanitizeName(String name) {
        String cleaned = StrUtil.blankToDefault(name, "file").replaceAll("[/\\\\\\p{Cntrl}]", "_").trim();
        if (cleaned.isEmpty()) {
            cleaned = "file";
        }
        return cleaned.length() > 80 ? cleaned.substring(cleaned.length() - 80) : cleaned;
    }

    /** 会话 id 净化：仅字母数字下划线连字符（路径片段，防穿越；bot 侧拼 outputs/&lt;chatId&gt; 前必须过这里） */
    public static String safeDir(String chatId) {
        String cleaned = StrUtil.blankToDefault(chatId, "default").replaceAll("[^a-zA-Z0-9_-]", "");
        return cleaned.isEmpty() ? "default" : cleaned.substring(0, Math.min(cleaned.length(), 64));
    }
}
