package com.mio.ai.superagent.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class CSAppControllerTest {

    @Autowired
    VectorStore vectorStore;

    @Test
    void doChat() {
        List<Document> documents = vectorStore.similaritySearch("m0NESY");
        System.out.println(documents);
    }
}