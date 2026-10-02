package com.mio.ai.framework.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author: Takina
 * @date: 2026/10/2
 * @description: 向量库装配。向量数据存 PostgreSQL（pgvector 扩展），与业务用的 MySQL 主数据源相互独立；
 *               自动配置的 PgVectorStore 会抓取主数据源的 JdbcTemplate，因此这里自定义 VectorStore Bean
 *               让其退避，并在方法内部组装独立的数据源（DataSource/JdbcTemplate 不注册为 Bean，
 *               否则 Spring Boot 的 DataSource/JdbcTemplate 自动配置会退避，影响 MySQL 与聊天记忆）。
 */
@Slf4j
@Configuration(enforceUniqueMethods = false)
public class VectorStoreConfig implements DisposableBean {

    private HikariDataSource vectorDataSource;

    @Bean
    public PgVectorStore vectorStore(EmbeddingModel embeddingModel,
                                     @Value("${mio.ai.vector.jdbc-url:jdbc:postgresql://localhost:5432/mio_ai_vec}") String jdbcUrl,
                                     @Value("${mio.ai.vector.username:postgres}") String username,
                                     @Value("${mio.ai.vector.password:mioai}") String password,
                                     @Value("${mio.ai.vector.dimensions:1024}") int dimensions) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setMaximumPoolSize(5);
        ds.setPoolName("mio-ai-vector");
        this.vectorDataSource = ds;

        // 启动时自动建扩展/表/HNSW 索引（CREATE EXTENSION 需要超级用户账号）
        return PgVectorStore.builder(new JdbcTemplate(ds), embeddingModel)
                .dimensions(dimensions)
                .initializeSchema(true)
                .build();
    }

    @Override
    public void destroy() {
        if (vectorDataSource != null) {
            vectorDataSource.close();
        }
    }
}
