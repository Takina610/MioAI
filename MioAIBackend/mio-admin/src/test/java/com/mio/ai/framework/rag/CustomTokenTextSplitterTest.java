package com.mio.ai.framework.rag;

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

    @Test
    void overlapDoesNotDuplicateSmallText() {
        // 回归：小于 chunkSize 的文本曾被切成两个几乎完全相同的块
        CustomTokenTextSplitter splitter = CustomTokenTextSplitter.builder()
                .withChunkSize(400)
                .withChunkOverlap(80)
                .build();
        String small = "星髓石是一种稀有的发光矿物，主要分布在幽蓝高原的裂谷地带。它由地脉热液与星尘残骸交感结晶而成。";
        List<String> chunks = splitter.apply(List.of(org.springframework.ai.document.Document.builder()
                .text(small)
                .build())).stream()
                .map(d -> d.getText())
                .toList();
        Assertions.assertEquals(1, chunks.size(), "小于 chunkSize 的文本只应产生一个块");
        Assertions.assertEquals(small, chunks.get(0));
    }

    @Test
    void overlapKeepsBoundaryContinuity() {
        CustomTokenTextSplitter splitter = CustomTokenTextSplitter.builder()
                .withChunkSize(100)
                .withChunkOverlap(20)
                .build();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            sb.append("这是第").append(i).append("句测试文本，用于验证重叠行为。");
        }
        List<String> chunks = splitter.apply(List.of(org.springframework.ai.document.Document.builder()
                .text(sb.toString())
                .build())).stream()
                .map(d -> d.getText())
                .toList();
        Assertions.assertTrue(chunks.size() > 1);
        // 相邻块应有重叠：后一块的开头内容应在前一块的结尾出现过。
        // token 边界可能把跨字节的中文字符切成替换符，比对前先清洗
        String head = chunks.get(1).substring(0, Math.min(12, chunks.get(1).length()))
                .replace("?", "").replace("\uFFFD", "");
        if (head.length() >= 4) {
            Assertions.assertTrue(chunks.get(0).contains(head),
                    "重叠区应让后一块的开头出现在前一块的尾部: " + head);
        }
    }

    @Test
    void smallChunkSizeStillAlignsToPunctuation() {
        // 回归：minChunkSizeChars 固定 350 时，chunkSize=200 的分块永远对不上句末标点
        CustomTokenTextSplitter splitter = CustomTokenTextSplitter.builder()
                .withChunkSize(200)
                .withChunkOverlap(40)
                .build();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("短句编号").append(i).append("，用于验证小分块的句末对齐。");
        }
        List<String> chunks = splitter.apply(List.of(org.springframework.ai.document.Document.builder()
                .text(sb.toString())
                .build())).stream()
                .map(d -> d.getText())
                .toList();
        Assertions.assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            Assertions.assertTrue(chunk.endsWith("。"),
                    "每个块都应以句末标点收尾: " + chunk.substring(Math.max(0, chunk.length() - 12)));
        }
    }
}
