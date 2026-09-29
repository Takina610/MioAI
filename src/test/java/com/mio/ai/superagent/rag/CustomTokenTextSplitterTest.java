package com.mio.ai.superagent.rag;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 自定义分块器纯单元测试（jtokkit 本地编码，不依赖外部环境）
 */
class CustomTokenTextSplitterTest {

    @Test
    void splitsLongTextIntoChunks() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("这是第").append(i).append("句测试文本，用于验证分块器的行为。");
        }
        CustomTokenTextSplitter splitter = CustomTokenTextSplitter.builder()
                .withChunkSize(100)
                .build();
        List<String> chunks = splitter.apply(List.of(org.springframework.ai.document.Document.builder()
                .text(sb.toString())
                .build())).stream()
                .map(d -> d.getText())
                .toList();

        Assertions.assertTrue(chunks.size() > 1, "长文本应被切分为多个分块");
        for (String chunk : chunks) {
            Assertions.assertFalse(chunk.isBlank());
        }
    }

    @Test
    void shortTextStaysSingleChunk() {
        CustomTokenTextSplitter splitter = CustomTokenTextSplitter.builder()
                .withChunkSize(800)
                .build();
        List<String> chunks = splitter.apply(List.of(org.springframework.ai.document.Document.builder()
                .text("短文本，不需要分块。")
                .build())).stream()
                .map(d -> d.getText())
                .toList();
        Assertions.assertEquals(1, chunks.size());
    }

    @Test
    void emptyTextProducesNoChunks() {
        CustomTokenTextSplitter splitter = new CustomTokenTextSplitter();
        List<String> chunks = splitter.apply(List.of(org.springframework.ai.document.Document.builder()
                .text("   ")
                .build())).stream()
                .map(d -> d.getText())
                .toList();
        Assertions.assertTrue(chunks.isEmpty());
    }
}
