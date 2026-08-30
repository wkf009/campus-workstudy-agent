package com.workstudy.controller;

import com.workstudy.common.AccessDeniedException;
import com.workstudy.common.Result;
import com.workstudy.entity.Application;
import com.workstudy.entity.Job;
import com.workstudy.entity.User;
import com.workstudy.mapper.JobMapper;
import com.workstudy.service.ApplicationService;
import com.workstudy.service.UserService;
import com.workstudy.utils.JwtUtils;
import com.workstudy.vo.ApplicationVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final JobMapper jobMapper;
    private final ApplicationService applicationService;
    private final UserService userService;

    public ExportController(JobMapper jobMapper, ApplicationService applicationService, UserService userService) {
        this.jobMapper = jobMapper;
        this.applicationService = applicationService;
        this.userService = userService;
    }

    @GetMapping("/jobs/csv")
    public void exportJobsCsv(HttpServletResponse response) throws IOException {
        List<Job> jobs = jobMapper.selectAll();

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode("岗位列表.csv", StandardCharsets.UTF_8));
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write('﻿');

        StringBuilder sb = new StringBuilder();
        sb.append("ID,标题,描述,薪资,地点,名额,部门,状态,发布时间\n");
        for (Job job : jobs) {
            sb.append(job.getId()).append(",");
            sb.append(escapeCsv(job.getTitle())).append(",");
            sb.append(escapeCsv(job.getDescription())).append(",");
            sb.append(job.getSalary()).append(",");
            sb.append(escapeCsv(job.getLocation())).append(",");
            sb.append(job.getQuota()).append(",");
            sb.append(escapeCsv(job.getDepartmentName())).append(",");
            sb.append(getStatusText(job.getStatus())).append(",");
            sb.append(job.getCreateTime()).append("\n");
        }
        response.getWriter().write(sb.toString());
    }

    /**
     * 导出申请记录：带 userId 时仅允许本人或超管（防止越权导出他人申请）。
     */
    @GetMapping("/applications/csv")
    public void exportApplicationsCsv(@RequestParam(required = false) Long userId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (userId != null) {
            Long currentUserId = JwtUtils.getUserIdFromRequest(request);
            User currentUser = userService.selectById(currentUserId);
            boolean isOwner = currentUserId.equals(userId);
            boolean isAdmin = currentUser != null && currentUser.getRole() != null && currentUser.getRole() == 3;
            if (!isOwner && !isAdmin) {
                throw new AccessDeniedException("只能导出自己的申请记录");
            }
        }
        List<?> applications;
        if (userId != null) {
            applications = applicationService.getMyApplicationsWithDetails(userId);
        } else {
            applications = applicationService.getAllApplications();
        }

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode("申请记录.csv", StandardCharsets.UTF_8));
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write('﻿');

        StringBuilder sb = new StringBuilder();
        sb.append("ID,岗位,学生,状态,申请时间,审核时间\n");
        for (Object obj : applications) {
            if (obj instanceof ApplicationVO vo) {
                sb.append(vo.getId()).append(",");
                sb.append(escapeCsv(vo.getJobTitle())).append(",");
                sb.append(escapeCsv(vo.getStudentName())).append(",");
                sb.append(getAppStatusText(vo.getStatus())).append(",");
                sb.append(vo.getApplyTime()).append(",");
                sb.append(vo.getAuditTime() != null ? vo.getAuditTime() : "").append("\n");
            } else if (obj instanceof Application app) {
                sb.append(app.getId()).append(",");
                sb.append(app.getJobId()).append(",");
                sb.append(app.getUserId()).append(",");
                sb.append(getAppStatusText(app.getStatus())).append(",");
                sb.append(app.getApplyTime()).append(",");
                sb.append(app.getAuditTime() != null ? app.getAuditTime() : "").append("\n");
            }
        }
        response.getWriter().write(sb.toString());
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String getStatusText(Integer status) {
        return switch (status != null ? status : -1) {
            case 0 -> "待审批";
            case 1 -> "招聘中";
            case 2 -> "已结束";
            case 3 -> "已拒绝";
            case 4 -> "已招满";
            default -> "未知";
        };
    }

    private String getAppStatusText(Integer status) {
        return switch (status != null ? status : -1) {
            case 0 -> "待审";
            case 1 -> "通过";
            case 2 -> "拒绝";
            case 3 -> "取消";
            default -> "未知";
        };
    }
}
