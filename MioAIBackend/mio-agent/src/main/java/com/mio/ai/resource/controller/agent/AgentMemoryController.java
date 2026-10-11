package com.mio.ai.resource.controller.agent;

import com.mio.ai.common.common.BaseResponse;
import com.mio.ai.common.exception.BusinessException;
import com.mio.ai.common.exception.ErrorCode;
import com.mio.ai.common.utils.ResultUtils;
import com.mio.ai.framework.zagent.memory.AgentMemory;
import com.mio.ai.framework.zagent.tools.SandboxFs;
import com.mio.ai.framework.zagent.tools.ToolsetFactory;
import com.mio.ai.resource.service.security.AccessGuardService;
import com.mio.ai.user.utils.RedisComponent;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能体跨会话记忆接口：记忆文件活在沙箱 memory/&lt;agentId&gt;/（与会话注入同源），
 * 这里提供列表/读取/保存/删除，供智能体管理页管理。
 */
@Slf4j
@RestController
@RequestMapping("/agent-memory")
public class AgentMemoryController {

    private static final String LIST_COMMAND = "cd %s && stat -c '%%n|%%s|%%Y' *.md 2>/dev/null";

    @Resource
    private ToolsetFactory toolsetFactory;

    @Resource
    private AccessGuardService accessGuardService;

    @Resource
    private RedisComponent redisComponent;

    /** 记忆文件列表（含 MEMORY.md 索引） */
    @GetMapping("/{agentId:\\d+}")
    public BaseResponse<List<Map<String, Object>>> list(@PathVariable Long agentId,
                                                        HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentOwner(agentId, userId);
        SandboxFs fs = sandbox();
        String dir = AgentMemory.directory(agentId);
        List<Map<String, Object>> files = new ArrayList<>();
        if (!fs.stat(dir).exists()) {
            return ResultUtils.success(files);
        }
        SandboxFs.ExecResult result = fs.exec(String.format(LIST_COMMAND, quote(dir)), 10, null);
        for (String line : SandboxFs.splitLines(result.output())) {
            String[] parts = line.split("\\|");
            if (parts.length < 3) {
                continue;
            }
            try {
                files.add(Map.of(
                        "name", parts[0],
                        "size", Long.parseLong(parts[1]),
                        "mtime", Long.parseLong(parts[2]) * 1000,
                        "index", AgentMemory.INDEX_FILE.equals(parts[0])));
            } catch (NumberFormatException ignored) {
                // 跳过解析失败的行
            }
        }
        return ResultUtils.success(files);
    }

    /** 读取记忆文件内容 */
    @GetMapping("/{agentId:\\d+}/content")
    public BaseResponse<Map<String, Object>> content(@PathVariable Long agentId,
                                                     @RequestParam String name,
                                                     HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentOwner(agentId, userId);
        requireValidName(name);
        SandboxFs fs = sandbox();
        String path = AgentMemory.filePath(agentId, name);
        SandboxFs.FileStat stat = fs.stat(path);
        if (!stat.exists() || stat.directory()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记忆文件不存在");
        }
        if (stat.size() > AgentMemory.maxFileBytes()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件过大，超出可编辑上限");
        }
        return ResultUtils.success(Map.of("name", name, "content", fs.readAll(path)));
    }

    /** 保存（新建/覆盖）记忆文件；非索引文件自动在 MEMORY.md 登记指针行（与模型的记忆协议一致） */
    @PostMapping("/{agentId:\\d+}")
    public BaseResponse<Boolean> save(@PathVariable Long agentId,
                                      @RequestBody Map<String, String> body,
                                      HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentOwner(agentId, userId);
        String name = body.get("name");
        String content = body.getOrDefault("content", "");
        requireValidName(name);
        if (content.length() > AgentMemory.maxFileBytes()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容超出大小上限");
        }
        SandboxFs fs = sandbox();
        fs.exec("mkdir -p " + quote(AgentMemory.directory(agentId)), 10, null);
        fs.write(AgentMemory.filePath(agentId, name), content);
        if (!AgentMemory.INDEX_FILE.equals(name)) {
            upsertIndexLine(fs, agentId, name, content);
        }
        return ResultUtils.success(true);
    }

    /** 索引缺行时补一行指针；已有指向该文件的行则不动（避免覆盖模型维护的摘要） */
    private void upsertIndexLine(SandboxFs fs, Long agentId, String name, String content) {
        try {
            String indexPath = AgentMemory.filePath(agentId, AgentMemory.INDEX_FILE);
            if (!fs.stat(indexPath).exists()) {
                fs.write(indexPath, "# Memory Index\n");
            }
            String index = fs.readAll(indexPath);
            if (index.contains(name)) {
                return;
            }
            String description = frontmatterField(content, "description");
            String line = "- [" + name + "](" + name + ")"
                    + (description == null ? "" : " — " + description) + "\n";
            fs.write(indexPath, index.endsWith("\n") || index.isBlank()
                    ? index + line : index + "\n" + line);
        } catch (Exception e) {
            log.warn("登记记忆索引指针行失败, agentId={}, name={}: {}", agentId, name, e.getMessage());
        }
    }

    /** frontmatter 中单行字段值（description: xxx）；无 frontmatter/字段为空则 null */
    private String frontmatterField(String content, String field) {
        if (content == null) {
            return null;
        }
        String[] lines = content.split("\n", 40);
        if (lines.length == 0 || !lines[0].strip().equals("---")) {
            return null;
        }
        String prefix = field.toLowerCase() + ":";
        for (int i = 1; i < lines.length; i++) {
            String trimmed = lines[i].strip();
            if (trimmed.equals("---")) {
                break;
            }
            if (trimmed.toLowerCase().startsWith(prefix)) {
                String value = trimmed.substring(prefix.length()).strip();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }

    /** 删除记忆文件（索引 MEMORY.md 不可删；索引中指向该文件的行同步清除） */
    @DeleteMapping("/{agentId:\\d+}")
    public BaseResponse<Boolean> delete(@PathVariable Long agentId,
                                        @RequestParam String name,
                                        HttpServletRequest httpRequest) {
        Long userId = redisComponent.getUserId(httpRequest.getHeader("token"));
        accessGuardService.checkAgentOwner(agentId, userId);
        requireValidName(name);
        if (AgentMemory.INDEX_FILE.equals(name)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "记忆索引不可删除");
        }
        SandboxFs fs = sandbox();
        fs.exec("rm -f " + quote(AgentMemory.filePath(agentId, name)), 10, null);
        scrubIndex(fs, agentId, name);
        return ResultUtils.success(true);
    }

    /** 索引中去掉指向被删文件的指针行，保持索引与文件一致 */
    private void scrubIndex(SandboxFs fs, Long agentId, String name) {
        try {
            String indexPath = AgentMemory.filePath(agentId, AgentMemory.INDEX_FILE);
            if (!fs.stat(indexPath).exists()) {
                return;
            }
            String index = fs.readAll(indexPath);
            StringBuilder kept = new StringBuilder();
            for (String line : index.split("\n", -1)) {
                if (!line.contains(name)) {
                    kept.append(line).append('\n');
                }
            }
            fs.write(indexPath, kept.toString());
        } catch (Exception e) {
            log.warn("清理记忆索引指针行失败, agentId={}, name={}: {}", agentId, name, e.getMessage());
        }
    }

    private void requireValidName(String name) {
        if (!AgentMemory.isValidName(name)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "文件名不合法：仅允许字母/数字/点/下划线/连字符组成的 .md 文件");
        }
    }

    private SandboxFs sandbox() {
        SandboxFs fs = toolsetFactory.sandboxFs();
        if (fs == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "沙箱未启用，记忆功能不可用");
        }
        return fs;
    }

    private static String quote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
