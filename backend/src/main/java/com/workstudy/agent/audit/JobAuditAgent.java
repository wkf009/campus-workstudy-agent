package com.workstudy.agent.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.entity.AuditReport;
import com.workstudy.entity.Job;
import com.workstudy.entity.User;
import com.workstudy.llm.ChatService;
import com.workstudy.llm.PromptTemplates;
import com.workstudy.mapper.AuditReportMapper;
import com.workstudy.mapper.JobMapper;
import com.workstudy.service.NotificationService;
import com.workstudy.service.UserService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 岗位预审 Agent（Agent 2 核心）：
 * 规则引擎（确定性检查）+ LLM（开放性质量判断，结构化 JSON 输出）→ 合并生成审核报告 → 落库 + 通知超管。
 * Human-in-the-Loop：报告只做"建议"，审批动作由超管一键采纳（adopt）触发。
 */
@Service
public class JobAuditAgent {

    private static final String POLICY =
            "1. 时薪不得低于 ¥15/时；2. 必须提供联系人及电话；3. 不得出现刷单/传销等违规内容；" +
            "4. 岗位描述需明确工作内容与时间；5. 招聘名额 ≥1；6. 仅限校内合法勤工俭学场景";

    private static final String MODEL = "qwen-plus";

    private final JobMapper jobMapper;
    private final AuditRuleEngine ruleEngine;
    private final AuditReportMapper auditReportMapper;
    private final ChatService chatService;
    private final LlmJsonParser jsonParser;
    private final UserService userService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobAuditAgent(JobMapper jobMapper,
                         AuditRuleEngine ruleEngine,
                         AuditReportMapper auditReportMapper,
                         ChatService chatService,
                         LlmJsonParser jsonParser,
                         UserService userService,
                         NotificationService notificationService) {
        this.jobMapper = jobMapper;
        this.ruleEngine = ruleEngine;
        this.auditReportMapper = auditReportMapper;
        this.chatService = chatService;
        this.jsonParser = jsonParser;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    /**
     * 生成岗位预审报告（规则 + LLM），落库并通知超管。
     */
    public AuditReport generateJobReport(Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new RuntimeException("岗位不存在");
        }

        // 1. 规则预检（确定性）
        List<String> ruleIssues = ruleEngine.check(job);

        // 2. LLM 预审（开放性，结构化输出）
        String input = "岗位信息：" + jobText(job)
                + "\n发布部门：" + nvl(job.getDepartmentName())
                + "\n平台规则：" + POLICY
                + "\n相似岗位参考：无（历史数据不足，按规则兜底）";
        Map<String, Object> parsed;
        try {
            parsed = jsonParser.parseJsonObject(chatService.chat(PromptTemplates.JOB_AUDIT, input));
        } catch (Exception e) {
            parsed = Map.of();
        }

        // 3. 合并：LLM 为空时用规则结果兜底
        String suggestion = LlmJsonParser.str(parsed, "suggestion");
        if (suggestion == null || suggestion.isBlank()) {
            suggestion = ruleIssues.isEmpty() ? "PASS" : "SUPPLEMENT";
        }
        double score = parseScore(parsed.get("score"), ruleIssues.isEmpty() ? 80 : 55);

        List<String> reasons = LlmJsonParser.strList(parsed, "reasons");
        if (reasons.isEmpty()) {
            reasons = ruleIssues;
        }
        List<String> modifySuggestions = LlmJsonParser.strList(parsed, "suggestions");
        List<String> riskFlags = LlmJsonParser.strList(parsed, "riskFlags");
        for (String issue : ruleIssues) {
            if (!riskFlags.contains(issue)) {
                riskFlags.add(issue);
            }
        }

        AuditReport report = new AuditReport();
        report.setTargetType("job");
        report.setTargetId(jobId);
        report.setSuggestion(suggestion);
        report.setScore(BigDecimal.valueOf(Math.round(score * 100.0) / 100.0));
        report.setReasons(toJson(reasons));
        report.setSuggestions(toJson(modifySuggestions));
        report.setRiskFlags(toJson(riskFlags));
        report.setAgentModel(MODEL);
        upsert(report);

        notifyAdmins(job);
        return report;
    }

    /**
     * 采纳建议：PASS→审批通过；REJECT→审批拒绝；SUPPLEMENT→提示需人工补充。
     */
    public String adoptSuggestion(Long jobId) {
        AuditReport report = auditReportMapper.selectByTarget("job", jobId);
        if (report == null) {
            throw new RuntimeException("该岗位还没有 AI 预审报告，请先生成");
        }
        return switch (report.getSuggestion() == null ? "" : report.getSuggestion()) {
            case "PASS" -> {
                jobMapper.updateStatus(jobId, 1, "AI 预审通过（一键采纳）");
                yield "已按 AI 建议通过审批，岗位已发布（招聘中）";
            }
            case "REJECT" -> {
                jobMapper.updateStatus(jobId, 3, "AI 预审拒绝（一键采纳）");
                yield "已按 AI 建议拒绝该岗位";
            }
            case "SUPPLEMENT" -> "AI 建议补充信息后重审，请人工核实后处理";
            default -> "报告建议未知，请人工处理";
        };
    }

    private void notifyAdmins(Job job) {
        try {
            for (User admin : userService.getUsersByRole(3)) {
                notificationService.sendNotification(admin.getId(), "新岗位待审核（AI 报告已生成）",
                        "岗位《" + job.getTitle() + "》待审批，AI 预审报告已生成，请前往审核工作台处理。",
                        1, job.getId());
            }
        } catch (Exception e) {
            // 通知失败不影响主流程
        }
    }

    private void upsert(AuditReport report) {
        AuditReport existing = auditReportMapper.selectByTarget(report.getTargetType(), report.getTargetId());
        if (existing != null) {
            auditReportMapper.updateByTarget(report);
        } else {
            auditReportMapper.insert(report);
        }
    }

    private String jobText(Job job) {
        return "标题=" + nvl(job.getTitle())
                + "；描述=" + nvl(job.getDescription())
                + "；要求=" + nvl(job.getRequirements())
                + "；薪资=" + (job.getSalary() == null ? "未填" : job.getSalary() + "元/时")
                + "；地点=" + nvl(job.getLocation())
                + "；时间=" + nvl(job.getWorkTime())
                + "；名额=" + (job.getQuota() == null ? "未填" : job.getQuota())
                + "；联系人=" + nvl(job.getContactPerson())
                + "；电话=" + nvl(job.getContactPhone());
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
