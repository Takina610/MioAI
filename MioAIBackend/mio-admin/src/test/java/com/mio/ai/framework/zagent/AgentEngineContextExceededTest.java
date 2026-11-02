package com.mio.ai.framework.zagent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 上下文超限判定回归测试：宽泛子串会误判参数校验类错误，
 * 导致引擎做一次无用压缩并掩盖真实失败原因。
 */
class AgentEngineContextExceededTest {

    @Test
    void recognizesGenuineContextExceededErrors() {
        Assertions.assertTrue(AgentEngine.isContextExceeded(
                new RuntimeException("This model's maximum context length is 128000 tokens.")));
        Assertions.assertTrue(AgentEngine.isContextExceeded(
                new RuntimeException("{\"error\":{\"code\":\"context_length_exceeded\"}}")));
        Assertions.assertTrue(AgentEngine.isContextExceeded(
                new RuntimeException("prompt is too long: 210000 tokens > 200000 maximum")));
        Assertions.assertTrue(AgentEngine.isContextExceeded(
                new RuntimeException("输入内容过长，请缩短后重试")));
        Assertions.assertTrue(AgentEngine.isContextExceeded(
                new RuntimeException("上下文长度超出限制")));
    }

    @Test
    void nestedCauseIsAlsoInspected() {
        Throwable wrapped = new RuntimeException("wrap",
                new IllegalStateException("context window exceeded for this request"));
        Assertions.assertTrue(AgentEngine.isContextExceeded(wrapped));
    }

    @Test
    void validationErrorsAreNotMistakenForContextOverflow() {
        // 旧实现对 "input token"/"token limit"/"length exceeds"/"超过上限" 都会误判
        Assertions.assertFalse(AgentEngine.isContextExceeded(
                new RuntimeException("invalid input token at position 3")));
        Assertions.assertFalse(AgentEngine.isContextExceeded(
                new RuntimeException("max_tokens 超过上限")));
        Assertions.assertFalse(AgentEngine.isContextExceeded(
                new RuntimeException("field length exceeds 64 characters")));
        Assertions.assertFalse(AgentEngine.isContextExceeded(
                new RuntimeException("api key 无效")));
        Assertions.assertFalse(AgentEngine.isContextExceeded(null));
    }
}
