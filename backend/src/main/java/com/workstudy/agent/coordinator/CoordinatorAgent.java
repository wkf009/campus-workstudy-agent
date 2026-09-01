package com.workstudy.agent.coordinator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workstudy.agent.audit.JobAuditAgent;
import com.workstudy.agent.common.LlmJsonParser;
import com.workstudy.agent.jobmatch.JobWriterAgent;
import com.workstudy.agent.jobmatch.RecommendationService;
import com.workstudy.agent.jobmatch.StudentProfileService;
import com.workstudy.agent.jobmatch.dto.RecommendationVO;
import com.workstudy.agent.interview.InterviewAgent;
import com.workstudy.agent.notify.NotificationAgent;
import com.workstudy.entity.AgentTask;
import com.workstudy.entity.Application;
import com.workstudy.entity.AuditReport;
import com.workstudy.entity.Job;
import com.workstudy.entity.StudentProfile;
import com.workstudy.llm.ChatService;
import com.workstudy.llm.PromptTemplates;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
    private final ChatService chatService;
    private final LlmJsonParser jsonParser;
    private final NotificationAgent notificationAgent;
    private final InterviewAgent interviewAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CoordinatorAgent(AgentTaskMapper taskMapper,
                            JobAuditAgent jobAuditAgent,
                            JobWriterAgent jobWriterAgent,
                            ApplicationMapper applicationMapper,
                            JobMapper jobMapper,
                            StudentProfileService profileService,
                            RecommendationService recommendationService,
                            NotificationService notificationService,
                            ChatService chatService,
                            LlmJsonParser jsonParser,
                            NotificationAgent notificationAgent,
                            InterviewAgent interviewAgent) {
        this.taskMapper = taskMapper;
        this.jobAuditAgent = jobAuditAgent;
        this.jobWriterAgent = jobWriterAgent;
        this.applicationMapper = applicationMapper;
        this.jobMapper = jobMapper;
        this.profileService = profileService;
        this.recommendationService = recommendationService;
        this.notificationService = notificationService;
        this.chatService = chatService;
        this.jsonParser = jsonParser;
        this.notificationAgent = notificationAgent;
        this.interviewAgent = interviewAgent;
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
        if (result == 1) {
            planInterview(applicationId); // 录用 → 面试安排（M6）
            return;
        }
        if (result != 2) {
            return; // 仅拒绝触发撮合
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
            String rejectedTitle = rejectedJob != null ? rejectedJob.getTitle() : "该岗位";
            // NotificationAgent 个性化文案，失败回退模板
            NotificationAgent.NotificationDraft draft = notificationAgent.generateRematch(
                    "学生#" + app.getUserId(), rejectedTitle, titles);
            notificationService.sendNotification(app.getUserId(),
                    draft != null ? draft.getTitle() : "AI 为你找到替代岗位",
                    draft != null ? draft.getContent()
                            : ("你申请的《" + rejectedTitle + "》未通过。AI 根据你的求职画像推荐了替代岗位："
                            + titles + "，可一键转投。"),
                    2, app.getJobId());
            completeTask(task, toJson(alternatives));
            log.info("申请 {} 被拒后撮合完成，为学生 {} 推荐 {} 个替代岗位",
                    applicationId, app.getUserId(), alternatives.size());
        } catch (Exception e) {
            failTask(task, e.getMessage());
            log.warn("申请 {} 撮合失败（不影响审核）: {}", applicationId, e.getMessage());
        }
    }

    /**
     * 生命周期协作（场景 3）：岗位长期未招满 → 分析原因（同类数据对比）→ 生成调整建议 → 通知部门。
     * 由 ScheduledTasks 定时扫描触发，HITL：建议是否采纳由部门决定（编辑岗位/联系发布人）。
     */
    public void onJobLifecycle(Long jobId) {
        AgentTask task = startTask(AgentTask.TASK_LIFECYCLE, "job", jobId,
                "{\"event\":\"LIFECYCLE_SCAN\"}");
        if (task == null) {
            return;
        }
        try {
            Job job = jobMapper.selectById(jobId);
            if (job == null || job.getStatus() == null || job.getStatus() != 1) {
                completeTask(task, "{\"skip\":\"岗位非招聘中\"}");
                return;
            }
            // 同类数据（规则计算）
            double avgSalary = avgSalaryOfDepartment(job.getDepartmentId(), jobId);
            int appCount = applicationMapper.selectByJobId(jobId).size();
            long daysOpen = job.getUpdateTime() == null ? 0
                    : Math.max(0, ChronoUnit.DAYS.between(job.getUpdateTime(), LocalDateTime.now()));

            String input = "岗位信息：{标题=" + nvl(job.getTitle())
                    + "；部门=" + nvl(job.getDepartmentName())
                    + "；薪资=" + (job.getSalary() == null ? "未填" : job.getSalary() + "元/时")
                    + "；时间=" + nvl(job.getWorkTime())
                    + "；要求=" + nvl(job.getRequirements())
                    + "}\n同类数据：同部门在招岗位平均薪资=" + avgSalary + "元/时；该岗位累计申请数=" + appCount
                    + "；在招天数=" + daysOpen + "天";
            String llmText = chatService.chat(PromptTemplates.JOB_LIFECYCLE, input);
            Map<String, Object> parsed = jsonParser.parseJsonObject(llmText);
            String issue = LlmJsonParser.str(parsed, "issue");
            String suggestion = LlmJsonParser.str(parsed, "suggestion");

            if (job.getPublisherId() != null && suggestion != null && !suggestion.isBlank()) {
                NotificationAgent.NotificationDraft draft = notificationAgent.generateLifecycle(
                        job.getTitle(), nvl(issue), suggestion, (int) daysOpen);
                notificationService.sendNotification(job.getPublisherId(),
                        draft != null ? draft.getTitle() : "岗位长期未招满，AI 给出调整建议",
                        draft != null ? draft.getContent()
                                : ("《" + job.getTitle() + "》已招聘 " + daysOpen + " 天。AI 分析：" + nvl(issue)
                                + "。建议：" + suggestion),
                        1, jobId);
            }
            completeTask(task, toJson(parsed));
            log.info("岗位 {} 生命周期分析完成: issue={}", jobId, issue);
        } catch (Exception e) {
            failTask(task, e.getMessage());
            log.warn("岗位 {} 生命周期分析失败: {}", jobId, e.getMessage());
        }
    }

    /**
     * 录用 → 面试安排（M6）：InterviewAgent 生成时间建议+面试题 → 通知学生 → INTERVIEW_PLAN 留痕。
     */
    private void planInterview(Long applicationId) {
        AgentTask task = startTask(AgentTask.TASK_INTERVIEW_PLAN, "application", applicationId,
                "{\"event\":\"APPLICATION_ACCEPTED\"}");
        if (task == null) {
            return;
        }
        try {
            Application app = applicationMapper.selectById(applicationId);
            if (app == null || app.getUserId() == null) {
                completeTask(task, "{\"skip\":\"申请不存在\"}");
                return;
            }
            Job job = app.getJobId() != null ? jobMapper.selectById(app.getJobId()) : null;
            InterviewAgent.InterviewPlan plan = interviewAgent.plan(applicationId);
            if (plan == null || plan.getSuggestedTimes().isEmpty()) {
                completeTask(task, "{\"skip\":\"无可用面试时间\"}");
                return;
            }
            String times = plan.getSuggestedTimes().stream()
                    .map(t -> t.getSlot() + "（" + t.getReason() + "）")
                    .collect(Collectors.joining("；"));
            String jobTitle = job != null ? job.getTitle() : "该岗位";
            NotificationAgent.NotificationDraft draft = notificationAgent.generateInterview(
                    jobTitle, times, nvl(plan.getTips()));
            notificationService.sendNotification(app.getUserId(),
                    draft != null ? draft.getTitle() : "面试安排建议",
                    draft != null ? draft.getContent()
                            : ("恭喜！你已通过《" + jobTitle + "》的申请。AI 建议面试时间：" + times
                            + "；准备建议：" + nvl(plan.getTips())),
                    2, applicationId);
            completeTask(task, toJson(plan));
            log.info("申请 {} 录用后面试安排生成完成", applicationId);
        } catch (Exception e) {
            failTask(task, e.getMessage());
            log.warn("申请 {} 面试安排失败（不影响审核）: {}", applicationId, e.getMessage());
        }
    }

    /** 同部门在招岗位平均薪资（排除自身），无数据返回 0 */
    private double avgSalaryOfDepartment(Long departmentId, Long excludeJobId) {
        try {
            if (departmentId == null) {
                return 0;
            }
            List<Job> deptJobs = jobMapper.selectByDepartment(departmentId);
            double sum = 0;
            int count = 0;
            for (Job j : deptJobs) {
                if (j.getId() != null && j.getId().equals(excludeJobId)) continue;
                if (j.getStatus() != null && j.getStatus() == 1 && j.getSalary() != null) {
                    sum += j.getSalary().doubleValue();
                    count++;
                }
            }
            return count == 0 ? 0 : Math.round(sum / count * 100.0) / 100.0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String nvl(String s) {
        return s == null || s.isBlank() ? "无" : s;
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
