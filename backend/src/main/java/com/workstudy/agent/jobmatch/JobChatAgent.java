package com.workstudy.agent.jobmatch;

import com.workstudy.llm.PromptTemplates;
import com.workstudy.service.ApplicationService;
import com.workstudy.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话式求职助手（Agent 1 的对话形态）：
 * - Function Calling：注入 JobTools，LLM 自主决定调用工具完成"找岗→看详情→申请"闭环；
 * - 流式输出：ChatClient.stream() 逐 token 通过 SSE 推送给前端；
 * - 会话记忆：按用户维护滑动窗口历史（内存实现，单机够用）。
 */
@Service
public class JobChatAgent {

    private static final Logger log = LoggerFactory.getLogger(JobChatAgent.class);

    private static final int MAX_HISTORY = 20; // 每用户最多保留 20 条历史消息（10 轮）

    private final ChatClient.Builder chatClientBuilder;
    private final JobService jobService;
    private final ApplicationService applicationService;

    /** 会话记忆：userId -> 历史消息列表（UserMessage/AssistantMessage） */
    private final Map<Long, List<Message>> sessionHistories = new ConcurrentHashMap<>();

    public JobChatAgent(ChatClient.Builder chatClientBuilder,
                        JobService jobService,
                        ApplicationService applicationService) {
        this.chatClientBuilder = chatClientBuilder;
        this.jobService = jobService;
        this.applicationService = applicationService;
    }

    /**
     * 流式对话：将模型输出逐块通过 SSE 推送给客户端。
     */
    public void streamChat(Long userId, String userMessage, SseEmitter emitter) {
        JobTools tools = new JobTools(userId, jobService, applicationService);
        List<Message> history = sessionHistories.computeIfAbsent(userId, k -> new ArrayList<>());

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(PromptTemplates.CHAT_ASSISTANT_SYSTEM));
        messages.addAll(history);
        messages.add(new UserMessage(userMessage));

        StringBuilder replyBuilder = new StringBuilder();
        try {
            chatClientBuilder.build().prompt()
                    .messages(messages)
                    .tools(tools)
                    .stream()
                    .content()
                    .subscribe(
                        chunk -> {
                            replyBuilder.append(chunk);
                            safeSend(emitter, "delta", chunk);
                        },
                        error -> {
                            log.warn("对话流式输出异常: {}", error.getMessage());
                            safeSend(emitter, "error", "AI 服务暂时不可用，请稍后重试");
                            emitter.complete();
                        },
                        () -> {
                            // 会话记忆更新（滑动窗口截断）
                            history.add(new UserMessage(userMessage));
                            history.add(new AssistantMessage(replyBuilder.toString()));
                            while (history.size() > MAX_HISTORY) {
                                history.remove(0);
                            }
                            emitter.complete();
                        }
                    );
        } catch (Exception e) {
            log.error("对话初始化失败: {}", e.getMessage());
            safeSend(emitter, "error", "对话服务初始化失败，请检查通义千问 API Key 配置");
            emitter.complete();
        }
    }

    private void safeSend(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            // 客户端已断开，忽略
        }
    }
}
