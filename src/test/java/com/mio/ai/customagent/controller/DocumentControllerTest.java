package com.mio.ai.customagent.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class DocumentControllerTest {

    @Autowired
    VectorStore vectorStore;

    @Test
    void similaritySearch() {
        // 3. 相似性查询
        SearchRequest searchRequest = SearchRequest
                .builder().query("恋爱")
                .topK(5) // ? 返回分数前五的
                .similarityThreshold(0.3) // ? 分数大于 0.3 的
                .build();
        List<Document> results = vectorStore.similaritySearch(searchRequest);
        // 4.输出
        System.out.println(results);
    }
}