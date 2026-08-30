package com.workstudy.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 向量存储配置。
 * 开发/演示期使用 SimpleVectorStore（内存实现，重启后由 JobVectorService 重建索引）；
 * VectorStore 为 Spring AI 抽象接口，后续可无缝切换 pgvector / Redis / Milvus（仅换 Bean 实现）。
 */
@Configuration
public class AiVectorStoreConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
