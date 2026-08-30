package com.workstudy.agent.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.entity.Application;
import com.workstudy.entity.AuditReport;
import com.workstudy.entity.Job;
import com.workstudy.llm.ChatService;
import com.workstudy.llm.PromptTemplates;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.AuditReportMapper;
import com.workstudy.mapper.JobMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 申请匹配度评估 Agent（Agent 2 的辅助决策）：
 * 对比学生自荐信/简历与岗位要求，LLM 输出匹配分 + 匹配点/差距点 + 面试建议，
 * 帮助部门管理员按匹配度排序候选人。
 */
@Service
public class ApplicationMatchAgent {

    private static final String MODEL = "qwen-plus";

    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final AuditReportMapper auditReportMapper;
    private final ChatService chatService;
    private final LlmJsonParser jsonParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApplicationMatchAgent(ApplicationMapper applicationMapper,
                                 JobMapper jobMapper,
                                 AuditReportMapper auditReportMapper,
                                 ChatService chatService,
                                 LlmJsonParser jsonParser) {
        this.applicationMapper = applicationMapper;
        this.jobMapper = jobMapper;
        this.auditReportMapper = auditReportMapper;
        this.chatService = chatService;
        this.jsonParser = jsonParser;
    }

    /**
     * 评估申请匹配度，返回落库后的报告（score=匹配分）。
     */
    public AuditReport evaluateApplication(Long applicationId) {
        Application app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new RuntimeException("申请记录不存在");
        }
        Job job = app.getJobId() != null ? jobMapper.selectById(app.getJobId()) : null;
        if (job == null) {
            throw new RuntimeException("岗位不存在");
        }

        String input = "岗位要求：" + nvl(job.getRequirements())
                + "\n岗位描述：" + nvl(job.getDescription())
                + "\n学生自荐信：" + nvl(app.getCoverLetter())
                + "\n学生简历摘要：简历文件 " + nvl(app.getResumeUrl());
        Map<String, Object> parsed;
        try {
            parsed = jsonParser.parseJsonObject(chatService.chat(PromptTemplates.APPLICATION_MATCH, input));
        } catch (Exception e) {
            parsed = Map.of();
        }

        double score = parseScore(parsed.get("matchScore"), 60);
        List<String> matched = LlmJsonParser.strList(parsed, "matchedPoints");
        List<String> gaps = LlmJsonParser.strList(parsed, "gapPoints");
        List<String> hints = new ArrayList<>();
        Object hint = parsed.get("interviewHint");
        if (hint != null && !hint.toString().isBlank()) {
            hints.add(hint.toString());
        }

        AuditReport report = new AuditReport();
        report.setTargetType("application");
        report.setTargetId(applicationId);
        report.setSuggestion(String.valueOf(Math.round(score)));
        report.setScore(BigDecimal.valueOf(Math.round(score * 100.0) / 100.0));
        report.setReasons(toJson(matched));
        report.setSuggestions(toJson(gaps));
        report.setRiskFlags(toJson(hints));
        report.setAgentModel(MODEL);
        upsert(report);
        return report;
    }

    private void upsert(AuditReport report) {
        AuditReport existing = auditReportMapper.selectByTarget(report.getTargetType(), report.getTargetId());
        if (existing != null) {
            auditReportMapper.updateByTarget(report);
        } else {
            auditReportMapper.insert(report);
        }
    }

    private double parseScore(Object score, double fallback) {
        try {
            double v = Double.parseDouble(score.toString());
            return Math.max(0, Math.min(100, v));
        } catch (Exception e) {
            return fallback;
        }
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String nvl(String s) {
        return s == null || s.isBlank() ? "无" : s;
    }
}
