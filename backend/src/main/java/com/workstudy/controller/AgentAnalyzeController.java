package com.workstudy.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workstudy.agent.analyst.AnalystAgent;
import com.workstudy.aspect.LogOperation;
import com.workstudy.common.Result;
import com.workstudy.entity.AgentTask;
import com.workstudy.mapper.AgentTaskMapper;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.JobMapper;
import com.workstudy.mapper.UserMapper;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 6 分析/面试接口（M6 场景 6）：
 * POST /api/agent/analyze            —— 统计看板 AI 解读（AnalystAgent）
 * GET  /api/agent/interview/{appId}  —— 查询录用申请的 AI 面试安排建议（InterviewAgent 结果）
 */
@RestController
@RequestMapping("/api/agent")
public class AgentAnalyzeController {

    private final AnalystAgent analystAgent;
    private final UserMapper userMapper;
    private final JobMapper jobMapper;
    private final ApplicationMapper applicationMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentAnalyzeController(AnalystAgent analystAgent,
                                  UserMapper userMapper,
                                  JobMapper jobMapper,
                                  ApplicationMapper applicationMapper,
                                  AgentTaskMapper agentTaskMapper) {
        this.analystAgent = analystAgent;
        this.userMapper = userMapper;
        this.jobMapper = jobMapper;
        this.applicationMapper = applicationMapper;
        this.agentTaskMapper = agentTaskMapper;
    }

    /**
     * 统计看板 AI 解读：收集统计数据 → LLM 生成总览/趋势/异常/建议。
     */
    @LogOperation
    @PostMapping("/analyze")
    public Result<AnalystAgent.AnalysisResult> analyze() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("用户总数", userMapper.selectAll().size());
        stats.put("岗位总数", jobMapper.selectAll().size());
        stats.put("在招岗位数", jobMapper.selectByStatus(1).size());
        stats.put("申请总数", applicationMapper.selectAll().size());
        long rejected = applicationMapper.selectAll().stream()
                .filter(a -> a.getStatus() != null && a.getStatus() == 2)
                .count();
        stats.put("被拒申请数", rejected);
        stats.put("录用申请数", applicationMapper.selectAll().stream()
                .filter(a -> a.getStatus() != null && a.getStatus() == 1)
                .count());
        return Result.success(analystAgent.analyze(stats));
    }

    /**
     * 查询录用申请的 AI 面试安排建议。
     */
    @GetMapping("/interview/{applicationId}")
    public Result<Map<String, Object>> getInterviewPlan(@PathVariable Long applicationId) {
        AgentTask task = agentTaskMapper.selectLatest(AgentTask.TASK_INTERVIEW_PLAN, "application", applicationId);
        if (task == null || task.getResultJson() == null || task.getResultJson().isBlank()) {
            return Result.success(Map.of());
        }
        try {
            return Result.success(objectMapper.readValue(task.getResultJson(),
                    new TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            return Result.success(Map.of());
        }
    }
}
