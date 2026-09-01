package com.workstudy.service;

import com.workstudy.agent.coordinator.ApplicationProcessedEvent;
import com.workstudy.entity.Application;
import com.workstudy.entity.Job;
import com.workstudy.vo.ApplicationVO;
import com.workstudy.mapper.ApplicationMapper;
import com.workstudy.mapper.JobMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ApplicationService {
    @Autowired
    private ApplicationMapper applicationMapper;

    @Autowired
    private JobMapper jobMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    public List<Application> getApplicationsByStudent(Long studentId) {
        return applicationMapper.selectByStudentId(studentId);
    }
    
    public List<Application> getApplicationsByJob(Long jobId) {
        return applicationMapper.selectByJobId(jobId);
    }
    
    /**
     * 获取学生的申请记录（包含岗位名称）
     */
    public List<ApplicationVO> getMyApplicationsWithDetails(Long userId) {
        return applicationMapper.selectMyApplicationsWithDetails(userId);
    }
    
    /**
     * 获取部门下所有岗位的申请记录
     */
    public List<ApplicationVO> getApplicationsByDepartment(Long departmentId) {
        return applicationMapper.selectApplicationsByDepartment(departmentId);
    }
    
    public Application submitApplication(Application application) {
        application.setStatus(0); // 待处理
        application.setApplyTime(java.time.LocalDateTime.now());
        // 确保所有必要的字段都有值
        if (application.getResumeUrl() == null) {
            application.setResumeUrl("");
        }
        if (application.getCoverLetter() == null) {
            application.setCoverLetter("");
        }
        if (application.getAuditRemark() == null) {
            application.setAuditRemark("");
        }
        
        // 自动获取并设置岗位名称
        Job job = null;
        if (application.getJobId() != null && (application.getJobTitle() == null || application.getJobTitle().isEmpty())) {
            job = jobMapper.selectById(application.getJobId());
            if (job != null) {
                application.setJobTitle(job.getTitle());
            }
        }
        
        applicationMapper.insert(application);

        // 通知闭环：有新申请时通知岗位发布人（insert 之后 application.id 才有值）
        if (job != null && job.getPublisherId() != null) {
            notificationService.sendNotification(job.getPublisherId(), "收到新申请",
                    "学生#" + application.getUserId() + " 申请了您发布的岗位《" + job.getTitle() + "》。",
                    2, application.getId());
        }
        return application;
    }
    
    /**
     * 审核申请：更新状态 + 通知 + 配额检查 + 发布撮合/面试事件。
     * 注意必须 @Transactional：@TransactionalEventListener(AFTER_COMMIT) 只在事务提交后触发。
     */
    @org.springframework.transaction.annotation.Transactional
    public Application processApplication(Long applicationId, Integer result, String remark, Long auditorId) {
        Application application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new RuntimeException("申请记录不存在");
        }
        application.setStatus(result); // 1-已录用，2-已拒绝
        application.setAuditRemark(remark);
        application.setAuditTime(java.time.LocalDateTime.now());
        application.setAuditorId(auditorId);
        applicationMapper.update(application);

        // 通知闭环：审核结果通知申请人
        if (application.getUserId() != null) {
            Job job = application.getJobId() != null ? jobMapper.selectById(application.getJobId()) : null;
            String jobTitle = job != null ? job.getTitle() : ("岗位#" + application.getJobId());
            boolean approved = result != null && result == 1;
            String remarkText = (remark == null || remark.isEmpty()) ? "" : "，审核备注：" + remark;
            notificationService.sendNotification(application.getUserId(),
                    approved ? "申请通过" : "申请未通过",
                    "您对《" + jobTitle + "》的申请已" + (approved ? "通过" : "未通过") + remarkText + "。",
                    2, applicationId);
        }

        // 如果申请被录用，检查岗位是否已招满
        if (result == 1) {
            checkAndMarkJobAsFull(application.getJobId());
        }

        // 多 Agent 协作：发布申请处理事件，事务提交后由 CoordinatorAgent 异步处理
        // （被拒 → 撮合推荐替代岗位 M3；录用 → 面试安排 M6）
        eventPublisher.publishEvent(new ApplicationProcessedEvent(applicationId, result));

        return application;
    }
    
    /**
     * 检查并标记岗位为已招满状态
     */
    private void checkAndMarkJobAsFull(Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null || job.getQuota() == null || job.getQuota() <= 0) {
            return;
        }
        
        // 查询该岗位已录用的人数
        List<Application> applications = applicationMapper.selectByJobId(jobId);
        long acceptedCount = applications.stream()
                .filter(app -> app.getStatus() != null && app.getStatus() == 1)
                .count();
        
        // 如果已录用人数达到配额，将岗位状态改为已招满（4）
        if (acceptedCount >= job.getQuota() && job.getStatus() != 4) {
            job.setStatus(4);
            job.setRemark("该岗位已招满");
            jobMapper.update(job);
        }
    }
    
    public Application selectById(Long id) {
        return applicationMapper.selectById(id);
    }
    
    public int insert(Application application) {
        return applicationMapper.insert(application);
    }
    
    public int update(Application application) {
        return applicationMapper.update(application);
    }
    
    public int deleteById(Long id) {
        return applicationMapper.deleteById(id);
    }
    
    public List<Application> getAllApplications() {
        return applicationMapper.selectAll();
    }
}
