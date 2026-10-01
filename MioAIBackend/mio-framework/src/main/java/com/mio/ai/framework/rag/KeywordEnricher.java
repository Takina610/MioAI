package com.mio.ai.framework.rag;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author: Takina
 * @date: 2026/3/29 10:51
 * @description: 文档关键词增强器
 * * 自动为知识库文档提取关键词并加入元数据，优化向量检索效果
 */
@Component
public class KeywordEnricher {
    @Autowired
    private ChatModel chatModel;

    public List<Document> enrich(List<Document> documents){
        KeywordMetadataEnricher enricher = KeywordMetadataEnricher
                .builder(chatModel)
                .keywordCount(5)
                .build();
        return enricher.apply(documents);
    }
}