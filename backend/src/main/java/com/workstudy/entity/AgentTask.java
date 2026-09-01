package com.workstudy.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 任务记录（多 Agent 协作的基础设施）：
 * 每个 Agent 动作（预审/生成/撮合/通知/分析）落一条任务，
 * 用于留痕（可审计）、幂等（同目标同类型不重复跑）、防呆（attempt 限次）。
 */
@Data
public class AgentTask {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    // 任务类型常量
    public static final String TASK_JOB_WRITE = "JOB_WRITE";
    public static final String TASK_JOB_AUDIT = "JOB_AUDIT";
    public static final String TASK_APPLICATION_MATCH = "APPLICATION_MATCH";
    public static final String TASK_REMATCH = "REMATCH";
    public static final String TASK_INTERVIEW_PLAN = "INTERVIEW_PLAN";
    public static final String TASK_NOTIFY = "NOTIFY";
    public static final String TASK_ANALYZE = "ANALYZE";
    public static final String TASK_LIFECYCLE = "LIFECYCLE";

    private Long id;
    private String taskType;      // JOB_WRITE / JOB_AUDIT / APPLICATION_MATCH / REMATCH / INTERVIEW_PLAN / NOTIFY / ANALYZE / LIFECYCLE
    private String targetType;    // job / application / user / dept
    private Long targetId;
    private String status;
    private String inputJson;
    private String resultJson;
    private String errorMsg;
    private Integer attempt;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
