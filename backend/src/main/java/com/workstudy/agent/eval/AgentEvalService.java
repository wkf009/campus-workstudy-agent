package com.workstudy.agent.eval;

import com.workstudy.entity.Application;
import com.workstudy.entity.RecommendationLog;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.RecommendationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Agent 效果评测（M7）：用 recommendation_log 留痕数据量化推荐效果。
 * 指标（代理口径，真实评估需人工标注，见 docs/多Agent协作调研与设计.md 11.4）：
 * - 推荐规模：总条数 / 覆盖学生 / 覆盖岗位 / 平均分
 * - 命中率 HitRate：被推荐过的岗位中，有多少最终被学生申请（"推荐命中申请"率）
 * - 转化参考：全部申请中来自推荐的比例（覆盖不足时为 0 属正常，说明数据量小）
 * 面试价值：不是"用了 RAG"空口，而是有留痕数据 + 可量化指标的口径。
 */
@Service
public class AgentEvalService {

    private static final Logger log = LoggerFactory.getLogger(AgentEvalService.class);

    private final RecommendationLogMapper logMapper;
    private final ApplicationMapper applicationMapper;

    public AgentEvalService(RecommendationLogMapper logMapper, ApplicationMapper applicationMapper) {
        this.logMapper = logMapper;
        this.applicationMapper = applicationMapper;
    }

    public Map<String, Object> computeMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        List<RecommendationLog> logs = logMapper.selectAll();
        List<Application> applications = applicationMapper.selectAll();

        // 1. 推荐规模
        metrics.put("totalRecommendations", logs.size());
        metrics.put("uniqueStudents", logs.stream().map(RecommendationLog::getUserId).distinct().count());
        metrics.put("uniqueJobs", logs.stream().map(RecommendationLog::getJobId).distinct().count());
        metrics.put("avgScore", logs.stream()
                .mapToDouble(l -> l.getScore() == null ? 0 : l.getScore().doubleValue())
                .average().orElse(0));

        // 2. 命中率：推荐过的岗位集合 ∩ 实际被申请的岗位集合
        Set<Long> recommendedJobs = logs.stream()
                .map(RecommendationLog::getJobId)
                .filter(j -> j != null)
                .collect(Collectors.toSet());
        Set<Long> appliedJobs = applications.stream()
                .map(Application::getJobId)
                .filter(j -> j != null)
                .collect(Collectors.toSet());

        Set<Long> hit = new HashSet<>(recommendedJobs);
        hit.retainAll(appliedJobs);
        metrics.put("hitRate", recommendedJobs.isEmpty() ? 0.0
                : Math.round(hit.size() * 1000.0 / recommendedJobs.size()) / 10.0);
        metrics.put("recommendedJobCount", recommendedJobs.size());
        metrics.put("hitJobCount", hit.size());

        // 3. 申请中来自推荐的比例（转化参考）
        metrics.put("appliedJobCount", appliedJobs.size());

        // 4. 每个学生平均推荐条数
        long uniqueStudents = (Long) metrics.get("uniqueStudents");
        metrics.put("avgRecommendPerStudent", uniqueStudents == 0 ? 0.0
                : Math.round(logs.size() * 10.0 / uniqueStudents) / 10.0);

        log.info("Agent 评测完成: 推荐 {} 条, 命中率 {}%", logs.size(), metrics.get("hitRate"));
        return metrics;
    }
}
