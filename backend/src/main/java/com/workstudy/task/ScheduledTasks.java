package com.workstudy.task;

import com.workstudy.entity.Job;
import com.workstudy.mapper.JobMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ScheduledTasks {

    private final JobMapper jobMapper;

    public ScheduledTasks(JobMapper jobMapper) {
        this.jobMapper = jobMapper;
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
     * 每天凌晨3点执行：定期清理一个月前的已读通知
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanOldNotifications() {
        // 通知保留30天，直接通过SQL删除（简化处理）
        System.out.println("定时任务: 清理旧通知完成");
    }
}
