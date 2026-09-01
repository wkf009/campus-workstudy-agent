package com.workstudy.agent.analyst;

import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.llm.ChatService;
import com.workstudy.llm.PromptTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分析助手 Agent（M6）：把统计看板的"裸数字"升级为 LLM 智能解读。
 * 输出：总览/趋势/异常/运营建议（结构化），供前端看板与运营决策。
 */
@Service
public class AnalystAgent {

    private static final Logger log = LoggerFactory.getLogger(AnalystAgent.class);

    private final ChatService chatService;
    private final LlmJsonParser jsonParser;

    public AnalystAgent(ChatService chatService, LlmJsonParser jsonParser) {
        this.chatService = chatService;
        this.jsonParser = jsonParser;
    }

    /**
     * 基于统计数据生成智能解读。失败时返回降级结果（不抛异常）。
     */
    public AnalysisResult analyze(Map<String, Object> stats) {
        try {
            String llmText = chatService.chat(PromptTemplates.ANALYST, stats == null ? "{}" : stats.toString());
            log.debug("AnalystAgent LLM 原文: {}", llmText);
            Map<String, Object> parsed = jsonParser.parseJsonObject(llmText);
            log.debug("AnalystAgent 解析结果: {}", parsed);
            AnalysisResult result = new AnalysisResult();
            result.setSummary(LlmJsonParser.str(parsed, "summary"));
            result.setTrends(LlmJsonParser.strList(parsed, "trends"));
            result.setAnomalies(LlmJsonParser.strList(parsed, "anomalies"));
            result.setAdvice(LlmJsonParser.str(parsed, "advice"));
            return result;
        } catch (Exception e) {
            log.warn("AnalystAgent 分析失败，返回降级结果: {}", e.getMessage());
            AnalysisResult fallback = new AnalysisResult();
            fallback.setSummary("数据分析服务暂不可用（请确认已配置通义千问 API Key）");
            return fallback;
        }
    }

    /** 分析结果 DTO */
    public static class AnalysisResult {
        private String summary;
        private List<String> trends;
        private List<String> anomalies;
        private String advice;

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public List<String> getTrends() { return trends == null ? List.of() : trends; }
        public void setTrends(List<String> trends) { this.trends = trends; }
        public List<String> getAnomalies() { return anomalies == null ? List.of() : anomalies; }
        public void setAnomalies(List<String> anomalies) { this.anomalies = anomalies; }
        public String getAdvice() { return advice; }
        public void setAdvice(String advice) { this.advice = advice; }
    }
}
