package com.workstudy.controller;

import com.workstudy.aspect.RequireRole;
import com.workstudy.common.Result;
import com.workstudy.entity.User;
import com.workstudy.mapper.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final UserMapper userMapper;
    private final JobMapper jobMapper;
    private final ApplicationMapper applicationMapper;

    public StatsController(UserMapper userMapper, JobMapper jobMapper, ApplicationMapper applicationMapper) {
        this.userMapper = userMapper;
        this.jobMapper = jobMapper;
        this.applicationMapper = applicationMapper;
    }

    @RequireRole({1, 2, 3})
    @GetMapping("/stats/overview")
    public Result<Map<String, Object>> getOverview() {
        List<User> allUsers = userMapper.selectAll();
        Map<String, Object> stats = new HashMap<>();

        long studentCount = allUsers.stream().filter(u -> u.getRole() == 0 && u.getStatus() == 1).count();
        long mentorCount = allUsers.stream().filter(u -> u.getRole() == 1 && u.getStatus() == 1).count();
        long deptAdminCount = allUsers.stream().filter(u -> u.getRole() == 2 && u.getStatus() == 1).count();

        stats.put("totalUsers", allUsers.stream().filter(u -> u.getStatus() == 1).count());
        stats.put("studentCount", studentCount);
        stats.put("mentorCount", mentorCount);
        stats.put("deptAdminCount", deptAdminCount);

        stats.put("totalJobs", jobMapper.selectAll().size());
        stats.put("publishedJobs", jobMapper.selectByStatus(1).size());
        stats.put("pendingJobs", jobMapper.selectByStatus(0).size());

        stats.put("totalApplications", applicationMapper.selectAll().size());

        return Result.success(stats);
    }

    @RequireRole({1, 2, 3})
    @GetMapping("/stats/jobs-by-department")
    public Result<List<Map<String, Object>>> getJobsByDepartment() {
        List<Map<String, Object>> result = new ArrayList<>();
        var deptList = userMapper.selectAll().stream()
                .filter(u -> u.getDepartmentId() != null)
                .map(u -> u.getDepartmentId())
                .distinct()
                .toList();

        for (Long deptId : deptList) {
            List<com.workstudy.entity.Job> jobs = jobMapper.selectByDepartment(deptId);
            if (!jobs.isEmpty()) {
                Map<String, Object> item = new HashMap<>();
                item.put("departmentId", deptId);
                item.put("count", jobs.size());
                result.add(item);
            }
        }
        return Result.success(result);
    }

    @RequireRole({1, 2, 3})
    @GetMapping("/stats/applications-trend")
    public Result<List<Map<String, Object>>> getApplicationsTrend() {
        List<com.workstudy.entity.Application> allApps = applicationMapper.selectAll();
        Map<String, Long> dateCount = new TreeMap<>();

        for (com.workstudy.entity.Application app : allApps) {
            if (app.getApplyTime() != null) {
                String date = app.getApplyTime().toLocalDate().toString();
                dateCount.merge(date, 1L, Long::sum);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : dateCount.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", entry.getKey());
            item.put("count", entry.getValue());
            result.add(item);
        }
        return Result.success(result);
    }
}
