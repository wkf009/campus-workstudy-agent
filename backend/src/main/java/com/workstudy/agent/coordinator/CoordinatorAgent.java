package com.workstudy.agent.coordinator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workstudy.agent.audit.JobAuditAgent;
import com.workstudy.entity.AgentTask;
import com.workstudy.entity.AuditReport;
import com.workstudy.mapper.AgentTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    private final AgentTaskMapper taskMapper;
    private final JobAuditAgent jobAuditAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CoordinatorAgent(AgentTaskMapper taskMapper, JobAuditAgent jobAuditAgent) {
        this.taskMapper = taskMapper;
        this.jobAuditAgent = jobAuditAgent;
    }

    /**
     * 事件：岗位提交（场景 1 起点）→ 自动触发 AI 预审。
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
            completeTask(task, toJson(report));
            log.info("岗位 {} 提交后自动预审完成: {}", jobId, report.getSuggestion());
        } catch (Exception e) {
            failTask(task, e.getMessage());
            log.warn("岗位 {} 自动预审失败（不影响岗位提交）: {}", jobId, e.getMessage());
        }
    }

    /**
     * 事件：申请被处理（场景 2/6 的触发点）。
     * result=1 录用 → M6 面试安排；result=2 拒绝 → M3 替代推荐。
     * （M3/M6 填充具体协作，M1 先留钩子）
     */
    public void onApplicationProcessed(Long applicationId, int result) {
        log.debug("申请 {} 处理结果 {}，等待 M3/M6 协作接入", applicationId, result);
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
