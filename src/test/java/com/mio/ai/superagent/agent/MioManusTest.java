package com.mio.ai.superagent.agent;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MioManusTest {

    @Autowired
    MioManus mioManus;

    @Test
    void runStream() {
        String userPrompt = """
                我的另一半居住在江西宜春袁州区，请帮我找到 5 公里内合适的约会地点，
                并结合一些网络图片，制定一份详细的约会计划，
                并以 PDF 格式输出""";
        String answer = mioManus.run(userPrompt);
        Assertions.assertNotNull(answer);
    }
}