package com.mio.ai.superagent.advisor;

import com.mio.ai.customagent.rag.RerankClient;
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

    /**
     * 检索 + 重排 Advisor（自实现，替代 spring-ai-alibaba 的 RetrievalRerankAdvisor）
     */
    public static Advisor createRerankAdvisor(VectorStore vectorStore,
                                              RerankClient rerankClient,
                                              String rerankModel,
                                              int rerankTopN){
        return new RetrievalRerankAdvisor(vectorStore, rerankClient,
                SearchRequest.builder().topK(100).build(),
                new PromptTemplate(DEFAULT_RAG_PROMPT_TEMPLATE),
                rerankModel, rerankTopN);
    }

    private static final String DEFAULT_RAG_PROMPT_TEMPLATE = """
            【用户问题信息】
            {query}
            【上下文信息】
            {question_answer_context}

            回答请严格遵循以下规则：
            1. 若上下文包含与用户问题直接相关的信息，通过语义理解匹配后给出专业回答。
            2. 如果上下文无相关内容，结合你自己的知识，给出准确回答。
            3. 回答必须精准、简洁，不编造内容。
            4. 回复直接给出答案，不使用"根据上下文""根据信息"等冗余表述。
            """;
}
