package com.workstudy.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.workstudy.agent.coordinator.JobSubmittedEvent;
import com.workstudy.common.PageResult;
import com.workstudy.entity.Job;
import com.workstudy.mapper.JobMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class JobService {
    @Autowired
    private JobMapper jobMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public Job selectById(Long id) {
        return jobMapper.selectById(id);
    }

    public List<Job> getJobsByStatus(Integer status) {
        return jobMapper.selectByStatus(status);
    }

    public List<Job> getJobsByDepartment(Long departmentId) {
        return jobMapper.selectByDepartment(departmentId);
    }

    public List<Job> getJobsByPublisher(Long publisherId) {
        return jobMapper.selectByPublisher(publisherId);
    }

    public List<Job> getAllPublishedJobs() {
        return jobMapper.selectAllPublished();
    }

    /**
     * 学生浏览岗位（分页）：SQL 层完成搜索/筛选/排序，PageHelper 分页。
     * 返回 PageResult{records,total,page,pageSize}，records 中 remark="1" 表示本部门推荐。
     */
    public PageResult<Job> getPublishedJobsPage(Long studentDepartmentId, String keyword, Long departmentId, int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        List<Job> jobs;
        try {
            jobs = jobMapper.selectPublishedOrdered(studentDepartmentId, keyword, departmentId);
        } finally {
            PageHelper.clearPage(); // 防御性清理 ThreadLocal，避免残留影响其他查询
        }
        for (Job job : jobs) {
            boolean isSameDepartment = studentDepartmentId != null
                && job.getDepartmentId() != null
                && studentDepartmentId.equals(job.getDepartmentId());
            job.setRemark(isSameDepartment ? "1" : "0");
        }
        PageInfo<Job> pageInfo = new PageInfo<>(jobs);
        return PageResult.of(pageInfo.getList(), pageInfo.getTotal(), page, pageSize);
    }

    public List<Job> getAllPublishedJobsWithRecommendation(Long studentDepartmentId) {
        // 复制为可变列表：某些实现（如 List.of）返回不可变列表，直接 sort 会抛 UnsupportedOperationException
        List<Job> jobs = new ArrayList<>(jobMapper.selectAllPublished());
        for (Job job : jobs) {
            boolean isSameDepartment = studentDepartmentId != null
                && job.getDepartmentId() != null
                && studentDepartmentId.equals(job.getDepartmentId());
            job.setRemark(isSameDepartment ? "1" : "0");
        }
        jobs.sort((a, b) -> {
            boolean aIsSameDept = "1".equals(a.getRemark());
            boolean bIsSameDept = "1".equals(b.getRemark());
            if (aIsSameDept && !bIsSameDept) return -1;
            if (!aIsSameDept && bIsSameDept) return 1;
            if (a.getSalary() == null && b.getSalary() == null) return 0;
            if (a.getSalary() == null) return 1;
            if (b.getSalary() == null) return -1;
            return b.getSalary().compareTo(a.getSalary());
        });
        return jobs;
    }

    public List<Job> getAllJobs() {
        return jobMapper.selectAll();
    }

    @Transactional
    public Job submitJob(Job job) {
        job.setStatus(0);
        jobMapper.insert(job);
        // 多 Agent 协作：发布事件，事务提交后由 CoordinatorAgent 异步执行"预审-修订"协作流
        // （异步化避免 LLM 调用阻塞发布接口，见 docs/踩坑记录.md K-09）
        eventPublisher.publishEvent(new JobSubmittedEvent(job.getId()));
        return job;
    }

    @Transactional
    public Job auditJob(Long jobId, Integer result, String remark) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new RuntimeException("岗位不存在");
        }

        if (result != null) {
            if (result == 1) {
                job.setStatus(1);
            } else {
                job.setStatus(3);
            }
        }

        job.setRemark(remark);

        int updateResult = jobMapper.update(job);

        // 通知闭环：审批结果通知岗位发布人
        if (job.getPublisherId() != null) {
            String remarkText = (remark == null || remark.isEmpty()) ? "" : "，原因：" + remark;
            if (result != null && result == 1) {
                notificationService.sendNotification(job.getPublisherId(), "岗位审批通过",
                        "您发布的岗位《" + job.getTitle() + "》已通过审批，正式发布。", 1, job.getId());
            } else {
                notificationService.sendNotification(job.getPublisherId(), "岗位审批未通过",
                        "您发布的岗位《" + job.getTitle() + "》未通过审批" + remarkText + "。", 1, job.getId());
            }
        }

        return job;
    }

    /**
     * 重新发布岗位：将状态置为 1（招聘中）。
     * 修复：原逻辑误设为 2（已结束），与业务语义相反；已是招聘中则幂等返回。
     */
    @Transactional
    public Job publishJob(Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new RuntimeException("岗位不存在");
        }
        if (job.getStatus() != null && job.getStatus() == 1) {
            return job;
        }
        job.setStatus(1);
        jobMapper.update(job);

        if (job.getPublisherId() != null) {
            notificationService.sendNotification(job.getPublisherId(), "岗位已发布",
                    "您发布的岗位《" + job.getTitle() + "》已重新发布，招聘中。", 1, job.getId());
        }
        return job;
    }

    public int insert(Job job) {
        return jobMapper.insert(job);
    }

    public int update(Job job) {
        return jobMapper.update(job);
    }

    public int deleteById(Long id) {
        return jobMapper.deleteById(id);
    }

    @Transactional
    public boolean deleteFullJob(Long jobId, Long currentUserId, Integer currentRole, Long currentDepartmentId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new RuntimeException("岗位不存在");
        }
        
        if (job.getStatus() != 4) {
            throw new RuntimeException("只能删除已招满的岗位");
        }
        
        if (currentRole != 2 && currentRole != 1) {
            throw new RuntimeException("只有部门管理员或企业导师可以删除岗位");
        }
        
        if (!job.getDepartmentId().equals(currentDepartmentId)) {
            throw new RuntimeException("只能删除本部门的岗位");
        }
        
        return jobMapper.deleteById(jobId) > 0;
    }
}