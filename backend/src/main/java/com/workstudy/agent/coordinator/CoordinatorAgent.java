package com.workstudy.agent.coordinator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workstudy.agent.audit.JobAuditAgent;
import com.workstudy.agent.jobmatch.JobWriterAgent;
import com.workstudy.agent.jobmatch.RecommendationService;
import com.workstudy.agent.jobmatch.StudentProfileService;
import com.workstudy.agent.jobmatch.dto.RecommendationVO;
import com.workstudy.entity.AgentTask;
import com.workstudy.entity.Application;
import com.workstudy.entity.AuditReport;
import com.workstudy.entity.Job;
import com.workstudy.entity.StudentProfile;
import com.workstudy.mapper.AgentTaskMapper;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.JobMapper;
import com.workstudy.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CoordinatorAgent（多 Agent 协作的协调者）：
 * - 事件入口：业务动作发生后（岗位提交/申请处理/定时扫描）被调用；
 * - 决策编排：决定"该触发哪些领域 Agent 的协作"（场景 1/2/3 的流程驱动器）；
 * - 任务管理：每个 Agent 动作落一条 agent_task（留痕/幂等/防呆）；
 * - 核心原则：Agent 是"业务增强"，任何 Agent 异常都不能影响核心业务事务（全 try-catch 隔离）。
 */
@Service
public class CoordinatorAgent {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorAgent.class);

    /** SUPPLEMENT 自动修订上限（防 Agent 死循环烧钱） */
    private static final int MAX_REVISE_ROUNDS = 2;

    private final AgentTaskMapper taskMapper;
    private final JobAuditAgent jobAuditAgent;
    private final JobWriterAgent jobWriterAgent;
    private final ApplicationMapper applicationMapper;
    private final JobMapper jobMapper;
    private final StudentProfileService profileService;
    private final RecommendationService recommendationService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CoordinatorAgent(AgentTaskMapper taskMapper,
                            JobAuditAgent jobAuditAgent,
                            JobWriterAgent jobWriterAgent,
                            ApplicationMapper applicationMapper,
                            JobMapper jobMapper,
                            StudentProfileService profileService,
                            RecommendationService recommendationService,
                            NotificationService notificationService) {
        this.taskMapper = taskMapper;
        this.jobAuditAgent = jobAuditAgent;
        this.jobWriterAgent = jobWriterAgent;
        this.applicationMapper = applicationMapper;
        this.jobMapper = jobMapper;
        this.profileService = profileService;
        this.recommendationService = recommendationService;
        this.notificationService = notificationService;
    }

    /**
     * 事件监听：岗位提交事务提交后异步执行预审/修订协作流。
     * AFTER_COMMIT 保证岗位数据已可见；@Async 保证不阻塞用户请求（K-09 踩坑修复）。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobSubmittedEvent(JobSubmittedEvent event) {
        onJobSubmitted(event.getJobId());
    }

    /**
     * 事件：岗位提交（场景 1 起点）→ 自动预审 → SUPPLEMENT 时 JobWriterAgent 修订 → 重新预审（限 2 轮）。
     * 幂等：同岗位已有成功预审则跳过；异常只标记任务 FAILED，绝不影响岗位提交事务。
     */
    public void onJobSubmitted(Long jobId) {
        AgentTask task = startTask(AgentTask.TASK_JOB_AUDIT, "job", jobId,
                "{\"event\":\"JOB_SUBMITTED\"}");
        if (task == null) {
            return; // 幂等跳过
        }
        try {
            AuditReport report = jobAuditAgent.generateJobReport(jobId);

            // 发布-修订协作循环：SUPPLEMENT → JobWriterAgent 修订 → 重新预审
            int round = 0;
            while (report != null && "SUPPLEMENT".equals(report.getSuggestion()) && round < MAX_REVISE_ROUNDS) {
                round++;
                jobWriterAgent.reviseJob(jobId, report);
                report = jobAuditAgent.generateJobReport(jobId);
            }
            boolean needsHuman = report != null && "SUPPLEMENT".equals(report.getSuggestion());
            String resultJson = toJson(report);
            if (needsHuman) {
                resultJson = resultJson.replace("}", ",\"needsHuman\":true,\"reviseRounds\":" + round + "}");
                log.info("岗位 {} 修订 {} 轮后仍需人工处理", jobId, round);
            }
            completeTask(task, resultJson);
            log.info("岗位 {} 发布协作流完成: suggestion={}, reviseRounds={}",
                    jobId, report == null ? "null" : report.getSuggestion(), round);
        } catch (Exception e) {
            failTask(task, e.getMessage());
            log.warn("岗位 {} 发布协作流失败（不影响岗位提交）: {}", jobId, e.getMessage());
        }
    }

    /**
     * 事件：申请被处理（场景 2/6 的触发点）。
     * result=2 拒绝 → 撮合：根据学生画像推荐替代岗位并通知（M3）；
     * result=1 录用 → 面试安排（M6 填充）。
     * 异步执行，异常隔离，不影响审核事务。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationProcessedEvent(ApplicationProcessedEvent event) {
        onApplicationProcessed(event.getApplicationId(), event.getResult());
    }

    public void onApplicationProcessed(Long applicationId, int result) {
        if (result != 2) {
            return; // 仅"拒绝"触发撮合（M3）；录用留待 M6 面试安排
        }
        AgentTask task = startTask(AgentTask.TASK_REMATCH, "application", applicationId,
                "{\"event\":\"APPLICATION_REJECTED\",\"result\":" + result + "}");
        if (task == null) {
            return;
        }
        try {
            Application app = applicationMapper.selectById(applicationId);
            if (app == null || app.getUserId() == null) {
                completeTask(task, "{\"skip\":\"申请不存在\"}");
                return;
            }
            Job rejectedJob = app.getJobId() != null ? jobMapper.selectById(app.getJobId()) : null;
            // 学生画像（已有则复用；推荐内部自动懒加载画像与向量索引）
            StudentProfile profile = profileService.getOrBuildProfile(app.getUserId());
            List<RecommendationVO> alternatives = recommendationService.recommend(app.getUserId(), 2);

            if (alternatives.isEmpty()) {
                completeTask(task, "{\"recommendations\":[]}");
                log.info("学生 {} 被拒后无替代岗位可推荐", app.getUserId());
                return;
            }
            String titles = alternatives.stream()
                    .map(RecommendationVO::getTitle)
                    .collect(Collectors.joining("、"));
            notificationService.sendNotification(app.getUserId(), "AI 为你找到替代岗位",
                    "你申请的《" + (rejectedJob != null ? rejectedJob.getTitle() : "岗位") + "》未通过。" +
                            "AI 根据你的求职画像推荐了替代岗位：" + titles + "，可一键转投。",
                    2, app.getJobId());
            completeTask(task, toJson(alternatives));
            log.info("申请 {} 被拒后撮合完成，为学生 {} 推荐 {} 个替代岗位",
                    applicationId, app.getUserId(), alternatives.size());
        } catch (Exception e) {
            failTask(task, e.getMessage());
            log.warn("申请 {} 撮合失败（不影响审核）: {}", applicationId, e.getMessage());
        }
    }

    // ==================== 任务管理（留痕/幂等/防呆） ====================

    /**
     * 开启任务：同目标同类型已有 SUCCESS 任务则返回 null（幂等跳过）。
     */
    private AgentTask startTask(String taskType, String targetType, Long targetId, String inputJson) {
        AgentTask latest = taskMapper.selectLatest(taskType, targetType, targetId);
        if (latest != null && AgentTask.STATUS_SUCCESS.equals(latest.getStatus())) {
            return null;
        }
        AgentTask task = new AgentTask();
        task.setTaskType(taskType);
        task.setTargetType(targetType);
        task.setTargetId(targetId);
        task.setStatus(AgentTask.STATUS_PENDING);
        task.setInputJson(inputJson);
        task.setAttempt(0);
        taskMapper.insert(task);
        return task;
    }

    private void completeTask(AgentTask task, String resultJson) {
        task.setStatus(AgentTask.STATUS_SUCCESS);
        task.setResultJson(resultJson);
        taskMapper.updateResult(task);
    }

    private void failTask(AgentTask task, String error) {
        task.setStatus(AgentTask.STATUS_FAILED);
        task.setErrorMsg(error == null ? null : error.length() > 500 ? error.substring(0, 500) : error);
        taskMapper.updateResult(task);
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }
}
