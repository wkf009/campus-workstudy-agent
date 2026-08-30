package com.workstudy.controller;

import com.workstudy.agent.jobmatch.JobVectorService;
import com.workstudy.agent.jobmatch.RecommendationService;
import com.workstudy.agent.jobmatch.StudentProfileService;
import com.workstudy.agent.jobmatch.dto.RecommendationVO;
import com.workstudy.aspect.LogOperation;
import com.workstudy.aspect.RequireRole;
import com.workstudy.common.Result;
import com.workstudy.entity.StudentProfile;
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

    public AgentRecommendController(RecommendationService recommendationService,
                                    StudentProfileService profileService,
                                    JobVectorService jobVectorService) {
        this.recommendationService = recommendationService;
        this.profileService = profileService;
        this.jobVectorService = jobVectorService;
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
}
