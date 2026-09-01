package com.workstudy.task;

import com.workstudy.agent.coordinator.CoordinatorAgent;
import com.workstudy.entity.Job;
import com.workstudy.mapper.JobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务：
 * 1) 凌晨 2 点：自动结束超 30 天未更新的招聘中岗位
 * 2) 凌晨 3 点：生命周期协作——扫描"招聘中超过 agent.lifecycle-days 天"的岗位，
 *    触发 CoordinatorAgent 做 AI 原因分析 + 调整建议（场景 3）
 */
@Component
public class ScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

    private final JobMapper jobMapper;
    private final CoordinatorAgent coordinatorAgent;

    @Value("${agent.lifecycle-days:20}")
    private int lifecycleDays;

    public ScheduledTasks(JobMapper jobMapper, CoordinatorAgent coordinatorAgent) {
        this.jobMapper = jobMapper;
        this.coordinatorAgent = coordinatorAgent;
    }

    /**
     * 每天凌晨2点执行：自动结束超过30天未更新的招聘中岗位
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void autoCloseExpiredJobs() {
        List<Job> publishedJobs = jobMapper.selectByStatus(1);
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        for (Job job : publishedJobs) {
            if (job.getUpdateTime() != null && job.getUpdateTime().isBefore(thirtyDaysAgo)) {
                job.setStatus(2);
                job.setRemark("系统自动结束（超过30天未更新）");
                jobMapper.update(job);
            }
        }
    }

    /**
     * 每天凌晨3点执行：生命周期协作（长期未招满岗位 → AI 分析 + 建议通知）。
     * 注意：逐个触发 CoordinatorAgent（内部异步 + 任务留痕 + 异常隔离）。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void lifecycleScan() {
        List<Job> publishedJobs = jobMapper.selectByStatus(1);
        LocalDateTime threshold = LocalDateTime.now().minusDays(lifecycleDays);
        int triggered = 0;
        for (Job job : publishedJobs) {
            if (job.getUpdateTime() != null && job.getUpdateTime().isBefore(threshold)) {
                coordinatorAgent.onJobLifecycle(job.getId());
                triggered++;
            }
        }
        log.info("生命周期扫描完成：检查 {} 个在招岗位，触发 {} 个生命周期分析", publishedJobs.size(), triggered);
    }
}
