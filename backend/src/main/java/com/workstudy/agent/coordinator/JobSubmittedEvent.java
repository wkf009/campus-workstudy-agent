package com.workstudy.agent.coordinator;

/**
 * 岗位提交事件（事务提交后由 CoordinatorAgent 异步处理预审/修订）。
 * 通过 Spring 事件机制解耦业务提交与 Agent 协作，避免 LLM 调用阻塞用户请求。
 */
public class JobSubmittedEvent {

    private final Long jobId;

    public JobSubmittedEvent(Long jobId) {
        this.jobId = jobId;
    }

    public Long getJobId() {
        return jobId;
    }
}
