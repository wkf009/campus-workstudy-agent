package com.workstudy.agent.notify;

import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.llm.ChatService;
import com.workstudy.llm.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知助手 Agent（M5）：把模板化通知升级为 LLM 个性化生成。
 * 由异步协作流（CoordinatorAgent）调用，不阻塞用户请求；
 * 失败时调用方回退模板文案（降级策略）。
 */
@Service
public class NotificationAgent {

    private static final Logger log = LoggerFactory.getLogger(NotificationAgent.class);

    private final ChatService chatService;
    private final LlmJsonParser jsonParser;

    public NotificationAgent(ChatService chatService, LlmJsonParser jsonParser) {
        this.chatService = chatService;
        this.jsonParser = jsonParser;
    }

    /**
     * 生成个性化通知文案。eventType 用于选择措辞侧重，context 提供业务数据。
     * 失败返回 null（调用方回退模板）。
     */
    public NotificationDraft generate(String eventType, Map<String, Object> context) {
        String input = "事件类型：" + eventType + "\n上下文：" + (context == null ? "{}" : context);
        try {
            String llmText = chatService.chat(PromptTemplates.NOTIFICATION_GENERATOR, input);
            Map<String, Object> parsed = jsonParser.parseJsonObject(llmText);
            String title = LlmJsonParser.str(parsed, "title");
            String content = LlmJsonParser.str(parsed, "content");
            if (title == null || title.isBlank() || content == null || content.isBlank()) {
                log.warn("NotificationAgent 生成结果为空，回退模板");
                return null;
            }
            return new NotificationDraft(title, content);
        } catch (Exception e) {
            log.warn("NotificationAgent 生成失败（回退模板）: {}", e.getMessage());
            return null;
        }
    }

    /** 便捷：生成撮合（替代岗位推荐）通知 */
    public NotificationDraft generateRematch(String studentName, String rejectedTitle, String alternatives) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("对象", "学生");
        ctx.put("场景", "申请未通过后平台自动撮合替代岗位");
        ctx.put("学生姓名", studentName);
        ctx.put("被拒岗位", rejectedTitle);
        ctx.put("AI 推荐替代岗位", alternatives);
        return generate("APPLICATION_REMATCH", ctx);
    }

    /** 便捷：生成生命周期（长期未招满）建议通知 */
    public NotificationDraft generateLifecycle(String jobTitle, String issue, String suggestion, int days) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("对象", "部门管理员");
        ctx.put("场景", "岗位长期未招满，AI 给出调整建议");
        ctx.put("岗位", jobTitle);
        ctx.put("在招天数", days);
        ctx.put("AI 分析原因", issue);
        ctx.put("AI 调整建议", suggestion);
        return generate("JOB_LIFECYCLE_ADVICE", ctx);
    }

    /** 通知文案 DTO */
    public static class NotificationDraft {
        private final String title;
        private final String content;

        public NotificationDraft(String title, String content) {
            this.title = title;
            this.content = content;
        }

        public String getTitle() { return title; }
        public String getContent() { return content; }
    }
}
