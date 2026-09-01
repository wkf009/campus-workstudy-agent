package com.workstudy.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workstudy.agent.jobmatch.JobVectorService;
import com.workstudy.agent.jobmatch.RecommendationService;
import com.workstudy.agent.jobmatch.StudentProfileService;
import com.workstudy.agent.jobmatch.dto.RecommendationVO;
import com.workstudy.aspect.LogOperation;
import com.workstudy.aspect.RequireRole;
import com.workstudy.common.Result;
import com.workstudy.entity.AgentTask;
import com.workstudy.entity.StudentProfile;
import com.workstudy.mapper.AgentTaskMapper;
import com.workstudy.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 1 智能求职推荐接口。
 * GET  /api/agent/recommendations    —— 我的个性化推荐（含可解释理由）
 * POST /api/agent/profile/rebuild    —— 重建我的求职画像（LLM 抽取）
 * POST /api/agent/index/rebuild      —— 重建岗位向量索引（发布方/管理员）
 */
@RestController
@RequestMapping("/api/agent")
public class AgentRecommendController {

    private final RecommendationService recommendationService;
    private final StudentProfileService profileService;
    private final JobVectorService jobVectorService;
    private final AgentTaskMapper agentTaskMapper;
    private final com.workstudy.agent.coordinator.CoordinatorAgent coordinatorAgent;
    private final com.workstudy.mapper.JobMapper jobMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @org.springframework.beans.factory.annotation.Value("${agent.lifecycle-days:20}")
    private int lifecycleDays;

    public AgentRecommendController(RecommendationService recommendationService,
                                    StudentProfileService profileService,
                                    JobVectorService jobVectorService,
                                    AgentTaskMapper agentTaskMapper,
                                    com.workstudy.agent.coordinator.CoordinatorAgent coordinatorAgent,
                                    com.workstudy.mapper.JobMapper jobMapper) {
        this.recommendationService = recommendationService;
        this.profileService = profileService;
        this.jobVectorService = jobVectorService;
        this.agentTaskMapper = agentTaskMapper;
        this.coordinatorAgent = coordinatorAgent;
        this.jobMapper = jobMapper;
    }

    /**
     * 我的智能推荐：多路召回 + LLM 精排，返回 Top-N（含推荐理由）。
     */
    @LogOperation
    @GetMapping("/recommendations")
    public Result<List<RecommendationVO>> recommend(
            @RequestParam(defaultValue = "5") int topN,
            HttpServletRequest request) {
        if (topN < 1 || topN > 20) topN = 5;
        Long userId = JwtUtils.getUserIdFromRequest(request);
        return Result.success(recommendationService.recommend(userId, topN));
    }

    /**
     * 重建我的画像（资料/行为变化后调用）。
     */
    @LogOperation
    @PostMapping("/profile/rebuild")
    public Result<StudentProfile> rebuildProfile(HttpServletRequest request) {
        Long userId = JwtUtils.getUserIdFromRequest(request);
        return Result.success(profileService.buildProfile(userId));
    }

    /**
     * 重建岗位向量索引（部门管理员/导师/超管）。
     */
    @LogOperation
    @RequireRole({1, 2, 3})
    @PostMapping("/index/rebuild")
    public Result<Map<String, Object>> rebuildIndex() {
        jobVectorService.indexAllJobs();
        Map<String, Object> data = new HashMap<>();
        data.put("message", "岗位向量索引重建完成");
        return Result.success(data);
    }

    /**
     * 查询某次申请被拒后的 AI 替代推荐（场景 2：撮合结果，供一键转投）。
     */
    @GetMapping("/rematch/{applicationId}")
    public Result<List<RecommendationVO>> getRematch(@PathVariable Long applicationId) {
        AgentTask task = agentTaskMapper.selectLatest(AgentTask.TASK_REMATCH, "application", applicationId);
        if (task == null || task.getResultJson() == null || task.getResultJson().isBlank()) {
            return Result.success(List.of());
        }
        try {
            return Result.success(objectMapper.readValue(task.getResultJson(),
                    new TypeReference<List<RecommendationVO>>() {}));
        } catch (Exception e) {
            return Result.success(List.of());
        }
    }

    /**
     * 查询岗位生命周期分析建议（场景 3：部门查看 AI 对长期未招满的分析）。
     */
    @GetMapping("/lifecycle/{jobId}")
    public Result<Map<String, Object>> getLifecycleAdvice(@PathVariable Long jobId) {
        AgentTask task = agentTaskMapper.selectLatest(AgentTask.TASK_LIFECYCLE, "job", jobId);
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

    /**
     * 手动触发生命周期扫描（场景 3 演示/调试）：扫描超过 lifecycle-days 未招满的在招岗位，
     * 触发 CoordinatorAgent 的 AI 分析 + 建议通知。
     */
    @LogOperation
    @RequireRole({2, 3})
    @PostMapping("/lifecycle/scan")
    public Result<Map<String, Object>> scanLifecycle() {
        List<com.workstudy.entity.Job> jobs = jobMapper.selectByStatus(1);
        java.time.LocalDateTime threshold = java.time.LocalDateTime.now().minusDays(lifecycleDays);
        int triggered = 0;
        for (com.workstudy.entity.Job job : jobs) {
            if (job.getUpdateTime() != null && job.getUpdateTime().isBefore(threshold)) {
                coordinatorAgent.onJobLifecycle(job.getId());
                triggered++;
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("message", "生命周期扫描完成，触发 " + triggered + " 个岗位分析（阈值 " + lifecycleDays + " 天）");
        return Result.success(data);
    }
}
