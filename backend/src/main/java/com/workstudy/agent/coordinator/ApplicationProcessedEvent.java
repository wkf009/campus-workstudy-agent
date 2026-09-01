package com.workstudy.agent.coordinator;

/**
 * 申请处理事件（事务提交后由 CoordinatorAgent 异步处理撮合/面试协作）。
 * result=1 录用 → 面试安排（M6）；result=2 拒绝 → 替代推荐撮合（M3）。
 */
public class ApplicationProcessedEvent {

    private final Long applicationId;
    private final int result;

    public ApplicationProcessedEvent(Long applicationId, int result) {
        this.applicationId = applicationId;
        this.result = result;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public int getResult() {
        return result;
    }
}
