package com.workstudy.agent.search;

import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.llm.ChatService;
import com.workstudy.llm.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 搜索理解 Agent（M5）：把学生的自然语言查询解析为结构化检索参数。
 * 例如"晚上和周末能做的、离图书馆近的兼职" →
 * {keyword:"兼职", location:"图书馆", timePref:"晚上/周末", ...}。
 * 解析失败时降级为"整句作为关键词"（保证搜索可用）。
 */
@Service
public class QueryAgent {

    private static final Logger log = LoggerFactory.getLogger(QueryAgent.class);

    private final ChatService chatService;
    private final LlmJsonParser jsonParser;

    public QueryAgent(ChatService chatService, LlmJsonParser jsonParser) {
        this.chatService = chatService;
        this.jsonParser = jsonParser;
    }

    public QueryIntent parse(String query) {
        try {
            String llmText = chatService.chat(PromptTemplates.QUERY_PARSER, query);
            Map<String, Object> parsed = jsonParser.parseJsonObject(llmText);

            QueryIntent intent = new QueryIntent();
            intent.setKeyword(LlmJsonParser.str(parsed, "keyword"));
            intent.setLocation(LlmJsonParser.str(parsed, "location"));
            intent.setTimePref(LlmJsonParser.str(parsed, "timePref"));
            intent.setSalaryMin(parseNumber(parsed.get("salaryMin")));
            intent.setSalaryMax(parseNumber(parsed.get("salaryMax")));
            intent.setSummary(LlmJsonParser.str(parsed, "summary"));
            if (intent.getKeyword() == null && intent.getLocation() == null) {
                // 降级：整句作为关键词
                intent.setKeyword(query);
            }
            if (intent.getSummary() == null || intent.getSummary().isBlank()) {
                intent.setSummary("正在为你搜索：" + query);
            }
            return intent;
        } catch (Exception e) {
            log.warn("QueryAgent 解析失败，降级为整句关键词: {}", e.getMessage());
            QueryIntent fallback = new QueryIntent();
            fallback.setKeyword(query);
            fallback.setSummary("正在为你搜索：" + query);
            return fallback;
        }
    }

    private Integer parseNumber(Object o) {
        if (o == null) return null;
        try {
            return (int) Math.round(Double.parseDouble(o.toString()));
        } catch (Exception e) {
            return null;
        }
    }

    /** 结构化查询意图 */
    public static class QueryIntent {
        private String keyword;
        private String location;
        private String timePref;
        private Integer salaryMin;
        private Integer salaryMax;
        private String summary;

        public String getKeyword() { return keyword; }
        public void setKeyword(String keyword) { this.keyword = keyword; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getTimePref() { return timePref; }
        public void setTimePref(String timePref) { this.timePref = timePref; }
        public Integer getSalaryMin() { return salaryMin; }
        public void setSalaryMin(Integer salaryMin) { this.salaryMin = salaryMin; }
        public Integer getSalaryMax() { return salaryMax; }
        public void setSalaryMax(Integer salaryMax) { this.salaryMax = salaryMax; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
    }
}
