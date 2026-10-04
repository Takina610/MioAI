package com.mio.ai.framework.sandbox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author: Takina
 * @date: 2026/10/4
 * @description: Linux 沙箱配置：Agent 通过 SSH 在独立服务器上真实执行命令。
 * <p>沙箱是"最宽松也最危险"的工具，默认关闭，仅在明确配置私钥后启用
 * （毕设单用户场景，参考 grokbot/muse 的单会话沙箱定位）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mio.ai.sandbox")
public class SandboxProperties {

    /** 总开关：false 时沙箱工具不注册，环境信息也不提示沙箱可用 */
    private boolean enabled = false;

    /** SSH 主机 */
    private String host = "127.0.0.1";

    /** SSH 端口 */
    private int port = 22;

    /** SSH 用户 */
    private String user = "ecs-user";

    /** 私钥路径（PEM），空则尝试 ~/.ssh 默认密钥 */
    private String privateKeyPath = "";

    /** 私钥口令（无口令留空） */
    private String passphrase = "";

    /** 沙箱工作目录（相对用户家目录），所有命令在其中执行 */
    private String workdir = "sandbox";

    /** SSH 连接超时（毫秒） */
    private int connectTimeoutMs = 10000;

    /** 单条命令默认超时（秒） */
    private int execTimeoutSeconds = 120;

    /** 返回给模型的输出上限（字符），超出保留末尾 */
    private int maxOutputChars = 8000;
}
