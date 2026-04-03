package com.mio.ai.superagent.rag;

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
    public static QuestionAnswerAdvisor createQuestionAnswerAdvisor(VectorStore vectorStore){
        PromptTemplate promptTemplate = new PromptTemplate("""
                【用户问题信息】
                {query}
                【上下文信息】
                {question_answer_context}
               
                回答请严格遵循以下规则：
                1. 若上下文包含与用户问题直接相关的地图打法、道具点位、战术执行、选手数据、战队信息，通过语义理解匹配后给出专业回答。
                2. 如果上下文无相关内容，结合CS职业赛事知识，给出准确、实战可用、专业的分析。
                3. 回答必须精准、简洁、专业，不编造选手数据、战队战绩、战术点位。
                4. 回复直接给出答案，不使用“根据上下文”“根据信息”等冗余表述。
                5. 语气保持专业、清晰、干练，符合职业教练/数据分析师风格，便于用户直接用于实战。
                6. 若用户问题与CS无关，礼貌告知仅提供CS比赛相关咨询服务。
                """);
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
