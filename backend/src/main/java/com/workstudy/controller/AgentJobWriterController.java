package com.workstudy.controller;

import com.workstudy.agent.jobmatch.JobWriterAgent;
import com.workstudy.aspect.LogOperation;
import com.workstudy.aspect.RequireRole;
import com.workstudy.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 岗位助手 Agent 接口（场景 1：部门输入要点 → AI 生成岗位初稿）。
 * POST /api/agent/write-job {"keywords":"机房值班，晚上，计算机学院"}
 */
@RestController
@RequestMapping("/api/agent")
public class AgentJobWriterController {

    private final JobWriterAgent jobWriterAgent;

    public AgentJobWriterController(JobWriterAgent jobWriterAgent) {
        this.jobWriterAgent = jobWriterAgent;
    }

    @LogOperation
    @RequireRole({1, 2})
    @PostMapping("/write-job")
    public Result<JobWriterAgent.JobDraft> writeJob(@RequestBody(required = false) Map<String, String> body) {
        String keywords = body != null ? body.getOrDefault("keywords", "") : "";
        if (keywords.isBlank()) {
            return Result.error("请输入岗位要点（如：机房值班，晚上，计算机学院）");
        }
        return Result.success(jobWriterAgent.generateDraft(keywords));
    }
}
