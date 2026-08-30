package com.workstudy.controller;

import com.workstudy.agent.audit.ApplicationMatchAgent;
import com.workstudy.agent.audit.JobAuditAgent;
import com.workstudy.aspect.LogOperation;
import com.workstudy.aspect.RequireRole;
import com.workstudy.common.Result;
import com.workstudy.entity.AuditReport;
import com.workstudy.mapper.AuditReportMapper;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 2 智能审核接口。
 * POST /api/agent/audit/job/{jobId}/generate   —— 生成岗位 AI 预审报告（规则+LLM）
 * GET  /api/agent/audit/report/{type}/{id}     —— 查询审核报告
 * POST /api/agent/audit/job/{jobId}/adopt      —— 超管一键采纳 AI 建议（HITL）
 * POST /api/agent/audit/match/{applicationId}  —— 生成申请匹配度评估
 */
@RestController
@RequestMapping("/api/agent/audit")
public class AgentAuditController {

    private final JobAuditAgent jobAuditAgent;
    private final ApplicationMatchAgent applicationMatchAgent;
    private final AuditReportMapper auditReportMapper;

    public AgentAuditController(JobAuditAgent jobAuditAgent,
                                ApplicationMatchAgent applicationMatchAgent,
                                AuditReportMapper auditReportMapper) {
        this.jobAuditAgent = jobAuditAgent;
        this.applicationMatchAgent = applicationMatchAgent;
        this.auditReportMapper = auditReportMapper;
    }

    /**
     * 生成岗位预审报告（部门管理员/导师/超管可触发）。
     */
    @LogOperation
    @RequireRole({1, 2, 3})
    @PostMapping("/job/{jobId}/generate")
    public Result<AuditReport> generateJobReport(@PathVariable Long jobId) {
        return Result.success(jobAuditAgent.generateJobReport(jobId));
    }

    /**
     * 查询审核报告。
     */
    @GetMapping("/report/{targetType}/{targetId}")
    public Result<AuditReport> getReport(@PathVariable String targetType, @PathVariable Long targetId) {
        return Result.success(auditReportMapper.selectByTarget(targetType, targetId));
    }

    /**
     * 超管一键采纳 AI 建议（Human-in-the-Loop：AI 给建议，人做决定）。
     */
    @LogOperation
    @RequireRole({3})
    @PostMapping("/job/{jobId}/adopt")
    public Result<Map<String, Object>> adopt(@PathVariable Long jobId) {
        String message = jobAuditAgent.adoptSuggestion(jobId);
        Map<String, Object> data = new HashMap<>();
        data.put("message", message);
        return Result.success(data);
    }

    /**
     * 生成申请匹配度评估。
     */
    @LogOperation
    @RequireRole({1, 2, 3})
    @PostMapping("/match/{applicationId}")
    public Result<AuditReport> evaluateMatch(@PathVariable Long applicationId) {
        return Result.success(applicationMatchAgent.evaluateApplication(applicationId));
    }
}
