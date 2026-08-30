package com.workstudy.controller;

import com.workstudy.aspect.LogOperation;
import com.workstudy.aspect.RequireRole;
import com.workstudy.common.AccessDeniedException;
import com.workstudy.common.Result;
import com.workstudy.entity.Application;
import com.workstudy.entity.User;
import com.workstudy.service.ApplicationService;
import com.workstudy.service.UserService;
import com.workstudy.utils.JwtUtils;
import com.workstudy.vo.ApplicationVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final JwtUtils jwtUtils;
    private final UserService userService;

    public ApplicationController(ApplicationService applicationService, JwtUtils jwtUtils, UserService userService) {
        this.applicationService = applicationService;
        this.jwtUtils = jwtUtils;
        this.userService = userService;
    }

    @LogOperation
    @RequireRole({0})
    @PostMapping("/student/applications")
    public Result<Application> submitApplication(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        Long userId = extractUserId(httpRequest, request, "userId");
        if (userId == null) {
            return Result.error("无法获取用户ID");
        }
        Application application = new Application();
        application.setUserId(userId);
        application.setJobId(Long.parseLong(request.get("jobId").toString()));
        application.setResumeUrl((String) request.getOrDefault("resumeUrl", ""));
        application.setCoverLetter((String) request.getOrDefault("coverLetter", ""));
        return Result.success(applicationService.submitApplication(application));
    }

    /**
     * 查询学生申请记录：仅本人或超管可查（防止越权查看他人申请）。
     */
    @GetMapping("/student/applications/{userId}")
    public Result<List<ApplicationVO>> getStudentApplications(@PathVariable Long userId, HttpServletRequest request) {
        Long currentUserId = JwtUtils.getUserIdFromRequest(request);
        User currentUser = userService.selectById(currentUserId);
        boolean isOwner = currentUserId.equals(userId);
        boolean isAdmin = currentUser != null && currentUser.getRole() != null && currentUser.getRole() == 3;
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("只能查看自己的申请记录");
        }
        return Result.success(applicationService.getMyApplicationsWithDetails(userId));
    }

    @RequireRole({1, 2, 3})
    @GetMapping("/department/applications")
    public Result<List<ApplicationVO>> getDepartmentApplications(HttpServletRequest request) {
        Long currentUserId = JwtUtils.getUserIdFromRequest(request);
        User currentUser = userService.selectById(currentUserId);
        if (currentUser == null) {
            return Result.error("用户不存在");
        }
        if (currentUser.getRole() == 2 || currentUser.getRole() == 1) {
            if (currentUser.getDepartmentId() == null) {
                return Result.error("未设置所属部门");
            }
            return Result.success(applicationService.getApplicationsByDepartment(currentUser.getDepartmentId()));
        }
        // 超管查看全部
        return Result.success(applicationService.getAllApplications().stream().map(a -> {
            com.workstudy.vo.ApplicationVO vo = new com.workstudy.vo.ApplicationVO();
            vo.setId(a.getId());
            vo.setJobId(a.getJobId());
            vo.setUserId(a.getUserId());
            vo.setStatus(a.getStatus());
            vo.setApplyTime(a.getApplyTime());
            vo.setAuditTime(a.getAuditTime());
            vo.setAuditRemark(a.getAuditRemark());
            return vo;
        }).toList());
    }

    @LogOperation
    @RequireRole({1, 2})
    @PostMapping("/department/applications/process")
    public Result<Application> processApplication(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        Long auditorId = extractUserId(httpRequest, request, "auditorId");
        if (auditorId == null) {
            return Result.error("无法获取审核人ID");
        }
        Long applicationId = Long.parseLong(request.get("applicationId").toString());
        Integer result = Integer.parseInt(request.get("result").toString());
        String remark = (String) request.getOrDefault("remark", "");
        return Result.success(applicationService.processApplication(applicationId, result, remark, auditorId));
    }

    private Long extractUserId(HttpServletRequest httpRequest, Map<String, Object> request, String paramName) {
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                return jwtUtils.getUserIdFromToken(authHeader.substring(7));
            } catch (Exception ignored) {}
        }
        Object userIdObj = request.get(paramName);
        return userIdObj != null ? Long.parseLong(userIdObj.toString()) : null;
    }
}
