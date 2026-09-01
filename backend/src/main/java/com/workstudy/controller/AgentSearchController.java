package com.workstudy.controller;

import com.workstudy.agent.search.QueryAgent;
import com.workstudy.aspect.LogOperation;
import com.workstudy.common.Result;
import com.workstudy.entity.Job;
import com.workstudy.mapper.JobMapper;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索理解 Agent 接口（M5 场景 5）：
 * POST /api/agent/search {"query":"晚上能做的、离图书馆近的兼职"}
 * → QueryAgent 解析自然语言为结构化检索参数 → 检索 → 返回 {intent, jobs}
 */
@RestController
@RequestMapping("/api/agent")
public class AgentSearchController {

    private final QueryAgent queryAgent;
    private final JobMapper jobMapper;

    public AgentSearchController(QueryAgent queryAgent, JobMapper jobMapper) {
        this.queryAgent = queryAgent;
        this.jobMapper = jobMapper;
    }

    @LogOperation
    @PostMapping("/search")
    public Result<Map<String, Object>> search(@RequestBody(required = false) Map<String, String> body) {
        String query = body != null ? body.getOrDefault("query", "") : "";
        if (query.isBlank()) {
            return Result.error("请输入搜索内容");
        }
        QueryAgent.QueryIntent intent = queryAgent.parse(query);
        List<Job> jobs = jobMapper.selectPublishedByFilters(intent.getKeyword(), intent.getLocation());
        // 降级：location 过滤过严导致空结果时，放宽为仅按关键词检索（保证搜索可用）
        if (jobs.isEmpty() && intent.getLocation() != null && !intent.getLocation().isBlank()) {
            jobs = jobMapper.selectPublishedByFilters(intent.getKeyword(), null);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("intent", intent);
        data.put("jobs", jobs);
        data.put("summary", intent.getSummary());
        return Result.success(data);
    }
}
