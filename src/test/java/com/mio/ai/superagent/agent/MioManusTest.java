package com.mio.ai.superagent.agent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * MioManus 端到端集成测试。
 * <p>说明：MioManus 不是 Spring Bean（每次会话按请求新建实例），
 * 因此这里手动组装依赖，而不是 @Autowired 一个不存在的 Bean。
 * <p>该测试依赖 MySQL / Redis / DashScope API Key 等真实环境，
 * 仅在设置了 DASHSCOPE_API_KEY 环境变量时才会执行（本地联调用）。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class MioManusTest {

    @Autowired
    @Qualifier("commonTools")
    ToolCallback[] commonTools;

    @Autowired
    @Qualifier("mioManusChatClient")
    ChatClient mioManusChatClient;

    @Test
    void runStream() {
        MioManus mioManus = new MioManus(
                commonTools,
                mioManusChatClient
        );
        String userPrompt = """
                我的另一半居住在江西宜春袁州区，请帮我找到 5 公里内合适的约会地点，
                并结合一些网络图片，制定一份详细的约会计划，
                并以 PDF 格式输出""";
        String answer = mioManus.run(userPrompt);
        Assertions.assertNotNull(answer);
        // 显式规划：执行结束后任务清单应已建立
        Assertions.assertFalse(mioManus.getPlan().isEmpty(), "MioManus 应先创建显式任务清单");
    }
}
