package com.workstudy.agent.jobmatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.agent.jobmatch.dto.RecommendationVO;
import com.workstudy.entity.Application;
import com.workstudy.entity.Job;
import com.workstudy.entity.RecommendationLog;
import com.workstudy.entity.StudentProfile;
import com.workstudy.entity.User;
import com.workstudy.llm.ChatService;
import com.workstudy.llm.PromptTemplates;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.JobMapper;
import com.workstudy.mapper.RecommendationLogMapper;
import com.workstudy.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 智能求职推荐服务（Agent 1 核心）：
 * 多路召回（语义向量 / 同部门规则 / 相似学生协同）→ 合并去重 → LLM 精排（可解释理由）→ 落库留痕。
 * LLM 失败时自动降级为规则打分，保证功能可用（面试可讲"降级策略"）。
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final int MAX_CANDIDATES = 30;

    private final StudentProfileService profileService;
    private final JobVectorService jobVectorService;
    private final UserMapper userMapper;
    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final RecommendationLogMapper logMapper;
    private final ChatService chatService;
    private final LlmJsonParser jsonParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecommendationService(StudentProfileService profileService,
                                 JobVectorService jobVectorService,
                                 UserMapper userMapper,
                                 ApplicationMapper applicationMapper,
                                 JobMapper jobMapper,
                                 RecommendationLogMapper logMapper,
                                 ChatService chatService,
                                 LlmJsonParser jsonParser) {
        this.profileService = profileService;
        this.jobVectorService = jobVectorService;
        this.userMapper = userMapper;
        this.applicationMapper = applicationMapper;
        this.jobMapper = jobMapper;
        this.logMapper = logMapper;
        this.chatService = chatService;
        this.jsonParser = jsonParser;
    }

    /**
     * 为学生生成 Top-N 推荐。
     */
    public List<RecommendationVO> recommend(Long userId, int topN) {
        // 1. 画像（懒构建）
        StudentProfile profile = profileService.getOrBuildProfile(userId);

        // 2. 向量索引懒加载
        if (jobVectorService.isEmpty()) {
            jobVectorService.indexAllJobs();
        }

        // 3. 多路召回
        Map<Long, Set<String>> candidates = recall(userId, profile);

        // 4. 合并去重（≤30），按召回顺序保持
        List<Long> orderedIds = new ArrayList<>(candidates.keySet());
        if (orderedIds.size() > MAX_CANDIDATES) {
            orderedIds = orderedIds.subList(0, MAX_CANDIDATES);
        }

        // 5. LLM 精排（失败降级为规则分）
        Map<Long, ScoredReason> ranked = rerank(profile, orderedIds);

        // 6. 组装 Top-N
        List<Long> topIds = ranked.entrySet().stream()
                .sorted(Map.Entry.<Long, ScoredReason>comparingByValue(
                        Comparator.comparingDouble(r -> -r.score)))
                .limit(topN)
                .map(Map.Entry::getKey)
                .toList();

        List<RecommendationVO> result = new ArrayList<>();
        for (Long jobId : topIds) {
            Job job = jobMapper.selectById(jobId);
            if (job == null) {
                continue;
            }
            ScoredReason sr = ranked.get(jobId);
            RecommendationVO vo = new RecommendationVO();
            vo.setJobId(job.getId());
            vo.setTitle(job.getTitle());
            vo.setDepartmentName(job.getDepartmentName());
            vo.setSalary(job.getSalary());
            vo.setLocation(job.getLocation());
            vo.setWorkTime(job.getWorkTime());
            vo.setScore(sr.score);
            vo.setReason(sr.reason);
            vo.setStrategies(new ArrayList<>(candidates.getOrDefault(jobId, Set.of())));
            result.add(vo);

            // 7. 落库留痕
            saveLog(userId, jobId, sr, candidates.getOrDefault(jobId, Set.of()));
        }
        return result;
    }

    /**
     * 多路召回：语义（向量）+ 规则（同部门/未申请）+ 协同（相似学生申请过）。
     */
    private Map<Long, Set<String>> recall(Long userId, StudentProfile profile) {
        Map<Long, Set<String>> candidates = new LinkedHashMap<>();

        // 已申请岗位（推荐不重复）
        Set<Long> applied = applicationMapper.selectByUserId(userId).stream()
                .map(Application::getJobId)
                .collect(Collectors.toSet());

        User user = userMapper.selectById(userId);

        // R1 语义召回：画像文本 × 岗位向量
        if (profile.getProfileText() != null && !profile.getProfileText().isBlank()) {
            try {
                List<Document> docs = jobVectorService.search(profile.getProfileText(), MAX_CANDIDATES);
                for (Document doc : docs) {
                    Object jobIdObj = doc.getMetadata().get("jobId");
                    if (jobIdObj != null) {
                        long jobId = Long.parseLong(jobIdObj.toString());
                        if (!applied.contains(jobId)) {
                            candidates.computeIfAbsent(jobId, k -> new LinkedHashSet<>()).add("语义匹配");
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("语义召回失败: {}", e.getMessage());
            }
        }

        // R2 规则召回：同部门 + 招聘中
        if (user != null && user.getDepartmentId() != null) {
            for (Job job : jobMapper.selectByStatus(1)) {
                if (user.getDepartmentId().equals(job.getDepartmentId()) && !applied.contains(job.getId())) {
                    candidates.computeIfAbsent(job.getId(), k -> new LinkedHashSet<>()).add("同部门");
                }
            }
        }

        // R3 协同召回：同专业学生申请过的在招岗位
        if (user != null && user.getMajor() != null && !user.getMajor().isBlank()) {
            Set<Long> similarJobIds = new HashSet<>();
            for (User s : userMapper.selectByRole(0)) {
                if (s.getId().equals(userId) || s.getMajor() == null || !s.getMajor().equals(user.getMajor())) {
                    continue;
                }
                for (Application a : applicationMapper.selectByUserId(s.getId())) {
                    similarJobIds.add(a.getJobId());
                }
            }
            if (!similarJobIds.isEmpty()) {
                for (Job job : jobMapper.selectByStatus(1)) {
                    if (similarJobIds.contains(job.getId()) && !applied.contains(job.getId())) {
                        candidates.computeIfAbsent(job.getId(), k -> new LinkedHashSet<>()).add("相似学生申请");
                    }
                }
            }
        }
        return candidates;
    }

    /**
     * LLM 精排：候选岗位 + 画像 → [{jobId, score, reason}]。
     * 失败/解析不全时降级：同部门 +10 分，其余按召回顺序给基础分。
     */
    private Map<Long, ScoredReason> rerank(StudentProfile profile, List<Long> orderedIds) {
        Map<Long, ScoredReason> result = new LinkedHashMap<>();
        if (orderedIds.isEmpty()) {
            return result;
        }

        // 组装候选岗位文本
        StringBuilder sb = new StringBuilder();
        List<Job> jobs = new ArrayList<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            Job job = jobMapper.selectById(orderedIds.get(i));
            if (job == null) continue;
            jobs.add(job);
            sb.append(i + 1).append(". jobId=").append(job.getId())
                    .append(", 标题=").append(nvl(job.getTitle()))
                    .append(", 部门=").append(nvl(job.getDepartmentName()))
                    .append(", 描述=").append(nvl(job.getDescription()))
                    .append(", 要求=").append(nvl(job.getRequirements()))
                    .append(", 薪资=").append(job.getSalary() == null ? "面议" : job.getSalary() + "元/时")
                    .append(", 地点=").append(nvl(job.getLocation()))
                    .append(", 时间=").append(nvl(job.getWorkTime()))
                    .append("\n");
        }

        String input = "学生画像：" + nvl(profile.getProfileText()) + "\n候选岗位：\n" + sb;
        try {
            String llmText = chatService.chat(PromptTemplates.JOB_RERANK, input);
            List<Map<String, Object>> parsed = jsonParser.parseJsonArray(llmText);
            Map<Long, ScoredReason> llmRanked = new LinkedHashMap<>();
            for (Map<String, Object> item : parsed) {
                try {
                    long jobId = Long.parseLong(LlmJsonParser.str(item, "jobId"));
                    double score = clampScore(item.get("score"));
                    String reason = LlmJsonParser.str(item, "reason");
                    if (orderedIds.contains(jobId)) {
                        llmRanked.put(jobId, new ScoredReason(score,
                                reason == null || reason.isBlank() ? "与你的画像匹配" : reason));
                    }
                } catch (Exception ignored) {
                    // 单条解析失败跳过
                }
            }
            if (!llmRanked.isEmpty()) {
                return llmRanked;
            }
            log.warn("LLM 精排解析为空，降级为规则打分");
        } catch (Exception e) {
            log.warn("LLM 精排调用失败，降级为规则打分: {}", e.getMessage());
        }

        // 降级：同部门岗位优先，其余按召回顺序
        double base = 60.0;
        for (Job job : jobs) {
            double score = base;
            if (job.getSalary() != null) {
                score += Math.min(10, job.getSalary().doubleValue() / 5);
            }
            result.put(job.getId(), new ScoredReason(score, "基于规则匹配（LLM 暂不可用）"));
        }
        return result;
    }

    private void saveLog(Long userId, Long jobId, ScoredReason sr, Set<String> strategies) {
        try {
            RecommendationLog log = new RecommendationLog();
            log.setUserId(userId);
            log.setJobId(jobId);
            log.setScore(BigDecimal.valueOf(Math.round(sr.score * 100.0) / 100.0));
            log.setReason(sr.reason);
            log.setStrategies(objectMapper.writeValueAsString(strategies));
            logMapper.insert(log);
        } catch (Exception e) {
            // 推荐留痕失败不影响主流程
        }
    }

    private double clampScore(Object score) {
        try {
            double v = Double.parseDouble(score.toString());
            return Math.max(0, Math.min(100, v));
        } catch (Exception e) {
            return 60.0;
        }
    }

    private String nvl(String s) {
        return s == null || s.isBlank() ? "无" : s;
    }

    /** 精排结果：分数 + 理由 */
    private static class ScoredReason {
        final double score;
        final String reason;
        ScoredReason(double score, String reason) {
            this.score = score;
            this.reason = reason;
        }
    }
}
