package com.workstudy.controller;

import com.workstudy.aspect.LogOperation;
import com.workstudy.common.Result;
import com.workstudy.llm.ChatService;
import com.workstudy.llm.EmbeddingService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 链路冒烟接口：验证 Spring AI Alibaba + DashScope 连通性。
 * POST /api/llm/chat   —— 对话冒烟（返回模型回复与模型名）
 * POST /api/llm/embed  —— 向量化冒烟（返回向量维度，验证 embedding 链路）
 */
@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private final ChatService chatService;
    private final EmbeddingService embeddingService;

    public LlmController(ChatService chatService, EmbeddingService embeddingService) {
        this.chatService = chatService;
        this.embeddingService = embeddingService;
    }

    @LogOperation
    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody(required = false) Map<String, String> body) {
        String message = body != null ? body.getOrDefault("message", "你好，请用一句话介绍你自己") : "你好，请用一句话介绍你自己";
        String reply = chatService.chat(message);
        Map<String, Object> data = new HashMap<>();
        data.put("reply", reply);
        data.put("model", "qwen-plus");
        return Result.success(data);
    }

    @LogOperation
    @PostMapping("/embed")
    public Result<Map<String, Object>> embed(@RequestBody(required = false) Map<String, String> body) {
        String text = body != null ? body.getOrDefault("text", "图书馆助理岗位") : "图书馆助理岗位";
        float[] vector = embeddingService.embed(text);
        Map<String, Object> data = new HashMap<>();
        data.put("dimension", vector.length);
        data.put("text", text);
        data.put("model", "text-embedding-v3");
        return Result.success(data);
    }
}
