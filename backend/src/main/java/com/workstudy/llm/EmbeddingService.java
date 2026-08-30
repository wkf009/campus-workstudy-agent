package com.workstudy.llm;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文本向量化服务：封装 Spring AI EmbeddingModel（text-embedding-v3）。
 * 用于 P2 智能推荐 Agent 的画像/岗位向量化与语义检索。
 */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 单条文本向量化。
     */
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    /**
     * 批量向量化（异步任务/索引重建时使用，减少调用次数）。
     */
    public List<float[]> embedBatch(List<String> texts) {
        return embeddingModel.embed(texts);
    }
}
