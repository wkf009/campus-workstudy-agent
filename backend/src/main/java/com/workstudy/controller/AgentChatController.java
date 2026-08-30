package com.workstudy.controller;

import com.workstudy.agent.jobmatch.JobChatAgent;
import com.workstudy.aspect.LogOperation;
import com.workstudy.common.Result;
import com.workstudy.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Agent 1 对话式求职助手接口（SSE 流式）。
 * POST /api/agent/chat  {"message":"帮我找晚上能做的兼职"}
 * 返回 SSE 事件：delta（逐字文本）/ error（异常提示）
 */
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    private final JobChatAgent jobChatAgent;

    public AgentChatController(JobChatAgent jobChatAgent) {
        this.jobChatAgent = jobChatAgent;
    }

    @LogOperation
    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody(required = false) Map<String, String> body, HttpServletRequest request) {
        String message = body != null ? body.getOrDefault("message", "") : "";
        Long userId = JwtUtils.getUserIdFromRequest(request);

        SseEmitter emitter = new SseEmitter(120_000L);
        if (message.isBlank()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("消息不能为空"));
            } catch (Exception ignored) {
            }
            emitter.complete();
            return emitter;
        }
        jobChatAgent.streamChat(userId, message, emitter);
        return emitter;
    }
}
