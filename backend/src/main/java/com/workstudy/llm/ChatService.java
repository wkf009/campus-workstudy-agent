package com.workstudy.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

/**
 * LLM 对话服务：封装 Spring AI ChatModel（通义千问 DashScope）。
 * 同步单轮对话，供冒烟验证与后续 Agent 模块复用；
 * 对话式求职助手（流式 SSE）在 P3 阶段基于 ChatClient 扩展。
 */
@Service
public class ChatService {

    private final ChatModel chatModel;

    public ChatService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 单轮对话，返回模型回复文本。
     */
    public String chat(String message) {
        ChatResponse response = chatModel.call(new Prompt(message));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("LLM 返回为空");
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * 带系统提示词的单轮对话（Prompt 工程入口）。
     */
    public String chat(String systemPrompt, String userMessage) {
        ChatResponse response = chatModel.call(PromptTemplates.build(systemPrompt, userMessage));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("LLM 返回为空");
        }
        return response.getResult().getOutput().getText();
    }
}
