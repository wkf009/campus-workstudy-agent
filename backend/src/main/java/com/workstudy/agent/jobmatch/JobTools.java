package com.workstudy.agent.jobmatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workstudy.entity.Application;
import com.workstudy.entity.Job;
import com.workstudy.service.ApplicationService;
import com.workstudy.service.JobService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 对话式求职助手的工具集（Function Calling / Tool Use）。
 *
 * 设计要点：每次对话请求创建一个实例并绑定当前用户（userId），
 * Spring AI 自动完成"LLM 决定调用 → 执行工具 → 结果回传模型"的循环。
 * 所有工具返回 JSON 字符串，便于 LLM 阅读与再组织回答。
 */
public class JobTools {

    private static final int SEARCH_LIMIT = 5;

    private final Long userId;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JobTools(Long userId, JobService jobService, ApplicationService applicationService) {
        this.userId = userId;
        this.jobService = jobService;
        this.applicationService = applicationService;
    }

    /**
     * 搜索招聘中的岗位（关键词模糊匹配标题/描述/要求）。
     */
    @Tool(description = "搜索招聘中的校园岗位，按关键词匹配标题/描述/要求，返回岗位列表")
    public String searchJobs(@ToolParam(description = "搜索关键词，如\"图书馆\"\"晚上\"\"计算机\"，可为空字符串") String keyword) {
        try {
            List<Job> jobs = jobService.getAllPublishedJobs();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Job job : jobs) {
                if (keyword != null && !keyword.isBlank()
                        && !containsIgnoreCase(job.getTitle(), keyword)
                        && !containsIgnoreCase(job.getDescription(), keyword)
                        && !containsIgnoreCase(job.getRequirements(), keyword)) {
                    continue;
                }
                if (result.size() >= SEARCH_LIMIT) break;
                result.add(briefJob(job));
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\":\"搜索岗位失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取岗位详情。
     */
    @Tool(description = "获取指定岗位的详细信息（描述/要求/薪资/地点/时间/联系人）")
    public String getJobDetail(@ToolParam(description = "岗位ID") Long jobId) {
        try {
            Job job = jobService.selectById(jobId);
            if (job == null) {
                return "{\"error\":\"岗位不存在\"}";
            }
            Map<String, Object> m = new HashMap<>();
            m.put("jobId", job.getId());
            m.put("title", job.getTitle());
            m.put("departmentName", job.getDepartmentName());
            m.put("description", job.getDescription());
            m.put("requirements", job.getRequirements());
            m.put("salary", job.getSalary());
            m.put("location", job.getLocation());
            m.put("workTime", job.getWorkTime());
            m.put("quota", job.getQuota());
            m.put("contactPerson", job.getContactPerson());
            m.put("contactPhone", job.getContactPhone());
            m.put("status", job.getStatus());
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return "{\"error\":\"获取岗位详情失败\"}";
        }
    }

    /**
     * 查询当前用户的申请记录。
     */
    @Tool(description = "查询当前学生的申请记录（岗位、状态、审核备注）")
    public String getMyApplications() {
        try {
            List<Application> apps = applicationService.getApplicationsByStudent(userId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Application app : apps) {
                Map<String, Object> m = new HashMap<>();
                m.put("jobId", app.getJobId());
                m.put("jobTitle", app.getJobTitle());
                m.put("status", statusText(app.getStatus()));
                m.put("auditRemark", app.getAuditRemark());
                m.put("applyTime", app.getApplyTime());
                result.add(m);
            }
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\":\"查询申请记录失败\"}";
        }
    }

    /**
     * 为当前用户申请岗位（防重复申请）。
     */
    @Tool(description = "为当前学生申请指定岗位，岗位ID必填；重复申请会返回提示")
    public String submitApplication(@ToolParam(description = "要申请的岗位ID") Long jobId) {
        try {
            Job job = jobService.selectById(jobId);
            if (job == null) {
                return "{\"error\":\"岗位不存在\"}";
            }
            if (job.getStatus() == null || job.getStatus() != 1) {
                return "{\"error\":\"该岗位当前不在招聘中（状态：" + statusText(job.getStatus()) + "）\"}";
            }
            boolean alreadyApplied = applicationService.getApplicationsByStudent(userId).stream()
                    .anyMatch(a -> jobId.equals(a.getJobId()));
            if (alreadyApplied) {
                return "{\"error\":\"你已申请过该岗位，无需重复申请\"}";
            }
            Application app = new Application();
            app.setUserId(userId);
            app.setJobId(jobId);
            app.setResumeUrl("");
            app.setCoverLetter("通过 AI 求职助手申请");
            Application saved = applicationService.submitApplication(app);
            return "{\"success\":true,\"applicationId\":" + saved.getId()
                    + ",\"message\":\"已成功申请岗位《" + job.getTitle() + "》\"}";
        } catch (Exception e) {
            return "{\"error\":\"申请失败: " + e.getMessage() + "\"}";
        }
    }

    private Map<String, Object> briefJob(Job job) {
        Map<String, Object> m = new HashMap<>();
        m.put("jobId", job.getId());
        m.put("title", job.getTitle());
        m.put("departmentName", job.getDepartmentName());
        m.put("salary", job.getSalary());
        m.put("location", job.getLocation());
        m.put("workTime", job.getWorkTime());
        return m;
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        if (text == null) return false;
        return text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String statusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待审批";
            case 1 -> "招聘中";
            case 2 -> "已结束";
            case 3 -> "已拒绝";
            case 4 -> "已招满";
            default -> "未知";
        };
    }
}
