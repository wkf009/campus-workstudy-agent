package com.workstudy.controller;

import com.workstudy.aspect.LogOperation;
import com.workstudy.aspect.RequireRole;
import com.workstudy.common.Result;
import com.workstudy.entity.Job;
import com.workstudy.entity.User;
import com.workstudy.service.JobService;
import com.workstudy.service.UserService;
import com.workstudy.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class JobController {

    private final JobService jobService;
    private final UserService userService;
    private final JwtUtils jwtUtils;

    public JobController(JobService jobService, UserService userService, JwtUtils jwtUtils) {
        this.jobService = jobService;
        this.userService = userService;
        this.jwtUtils = jwtUtils;
    }

    /**
     * 学生浏览岗位（分页）：支持关键词搜索与部门筛选，本部门优先 + 薪资降序。
     * remark="1" 表示本部门推荐岗位。
     */
    @GetMapping("/student/jobs")
    public Result<com.workstudy.common.PageResult<Job>> getPublishedJobs(
            HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId) {
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 10;
        Long currentUserId = JwtUtils.getUserIdFromRequest(request);
        User currentUser = userService.selectById(currentUserId);
        Long studentDepartmentId = currentUser != null ? currentUser.getDepartmentId() : null;
        return Result.success(jobService.getPublishedJobsPage(studentDepartmentId, keyword, departmentId, page, pageSize));
    }

    @LogOperation
    @RequireRole({1, 2})
    @PostMapping("/department/jobs")
    public Result<Job> submitJob(@RequestBody Job job, HttpServletRequest request) {
        Long publisherId = JwtUtils.getUserIdFromRequest(request);
        job.setPublisherId(publisherId);
        return Result.success(jobService.submitJob(job));
    }

    @GetMapping("/department/jobs")
    public Result<List<Job>> getDepartmentJobs(@RequestParam Long publisherId) {
        return Result.success(jobService.getJobsByPublisher(publisherId));
    }

    @GetMapping("/department/jobs/all")
    public Result<List<Job>> getAllDepartmentJobs() {
        return Result.success(jobService.getAllJobs());
    }

    @GetMapping("/admin/jobs/pending")
    public Result<List<Job>> getPendingJobs() {
        return Result.success(jobService.getJobsByStatus(0));
    }

    @GetMapping("/admin/jobs/all")
    public Result<List<Job>> getAllJobs(HttpServletRequest request) {
        Long currentUserId = JwtUtils.getUserIdFromRequest(request);
        User currentUser = userService.selectById(currentUserId);
        if (currentUser == null) {
            return Result.error("用户不存在");
        }
        if (currentUser.getRole() == 2 || currentUser.getRole() == 1) {
            return Result.success(jobService.getJobsByDepartment(currentUser.getDepartmentId()));
        }
        return Result.success(jobService.getAllJobs());
    }

    @LogOperation
    @RequireRole({3})
    @PostMapping("/admin/jobs/audit")
    public Result<Job> auditJob(@RequestBody Map<String, Object> request) {
        Long jobId = Long.parseLong(request.get("jobId").toString());
        Integer result = null;
        if (request.containsKey("result")) {
            result = Integer.parseInt(request.get("result").toString());
        } else if (request.containsKey("status")) {
            result = Integer.parseInt(request.get("status").toString());
        }
        String remark = (String) request.getOrDefault("remark", "");
        return Result.success(jobService.auditJob(jobId, result, remark));
    }

    @LogOperation
    @RequireRole({3})
    @PostMapping("/admin/jobs/publish")
    public Result<Job> publishJob(@RequestBody Map<String, Object> request) {
        Long jobId = Long.parseLong(request.get("jobId").toString());
        return Result.success(jobService.publishJob(jobId));
    }

    @LogOperation
    @RequireRole({1, 2})
    @DeleteMapping("/department/jobs/{id}")
    public Result<Void> deleteFullJob(@PathVariable Long id, HttpServletRequest request) {
        Long currentUserId = JwtUtils.getUserIdFromRequest(request);
        User currentUser = userService.selectById(currentUserId);
        if (currentUser == null) {
            return Result.error("用户不存在");
        }
        jobService.deleteFullJob(id, currentUserId, currentUser.getRole(), currentUser.getDepartmentId());
        return Result.successMsg("岗位已删除");
    }
}
