package com.mio.ai.superagent.advisor;

import com.alibaba.cloud.ai.advisor.RetrievalRerankAdvisor;
import com.alibaba.cloud.ai.model.RerankModel;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * @author: Takina
 * @date: 2026/3/29 10:46
 * @description: 智能顾问工厂类，负责创建系统所需的问答顾问、重排顾问等实例
 */
public class AdvisorFactory {
    public static QuestionAnswerAdvisor createQuestionAnswerAdvisor(VectorStore vectorStore,
                                                                    PromptTemplate promptTemplate
    ){
        return QuestionAnswerAdvisor.builder(vectorStore)
                .promptTemplate(promptTemplate)
                .searchRequest(SearchRequest
                        .builder()
                        .similarityThreshold(0.4)
                        .topK(5)
                        .build())
                .build();
    }

    public static Advisor createRerankAdvisor(VectorStore vectorStore, RerankModel rerankModel){
        RetrievalRerankAdvisor advisor = new RetrievalRerankAdvisor(vectorStore, rerankModel,
                SearchRequest.builder().topK(100).build());
        return advisor;
    }
}
