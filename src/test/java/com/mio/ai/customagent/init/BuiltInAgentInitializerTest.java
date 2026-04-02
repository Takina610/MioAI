package com.mio.ai.customagent.init;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BuiltInAgentInitializerTest {

    @Autowired
    BuiltInAgentInitializer builtInAgentInitializer;

    @Test
    void run() {
        builtInAgentInitializer.run();
    }
}