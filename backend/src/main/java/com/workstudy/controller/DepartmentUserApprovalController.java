package com.workstudy.controller;

import com.workstudy.aspect.LogOperation;
import com.workstudy.aspect.RequireRole;
import com.workstudy.common.AccessDeniedException;
import com.workstudy.common.Result;
import com.workstudy.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import com.workstudy.entity.User;
import com.workstudy.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/department")
public class DepartmentUserApprovalController {

    private final UserService userService;

    public DepartmentUserApprovalController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 部门待审批学生列表：部门管理员只能查看本部门，超管可查看任意部门。
     */
    @RequireRole({2, 3})
    @GetMapping("/users/pending")
    public Result<List<User>> getPendingStudents(@RequestParam Long departmentId, HttpServletRequest request) {
        User currentUser = userService.selectById(JwtUtils.getUserIdFromRequest(request));
        if (currentUser == null) {
            return Result.error("用户不存在");
        }
        if (currentUser.getRole() == 2 && !departmentId.equals(currentUser.getDepartmentId())) {
            throw new AccessDeniedException("只能查看本部门的待审批学生");
        }
        List<User> pendingStudents = userService.getPendingUsers().stream()
                .filter(user -> user.getRole() == 0
                        && user.getDepartmentId() != null
                        && user.getDepartmentId().equals(departmentId))
                .collect(Collectors.toList());
        return Result.success(pendingStudents);
    }

    @LogOperation
    @RequireRole({2, 3})
    @PostMapping("/users/approve")
    public Result<Void> approveStudent(@RequestBody java.util.Map<String, Object> request, HttpServletRequest httpRequest) {
        Long userId = Long.parseLong(request.get("userId").toString());
        Integer status = Integer.parseInt(request.get("status").toString());
        Long departmentId = Long.parseLong(request.get("departmentId").toString());

        // 部门管理员只能审批本部门学生
        User currentUser = userService.selectById(JwtUtils.getUserIdFromRequest(httpRequest));
        if (currentUser != null && currentUser.getRole() == 2 && !departmentId.equals(currentUser.getDepartmentId())) {
            throw new AccessDeniedException("只能审批本部门的学生");
        }

        User user = userService.selectById(userId);
        if (user == null || user.getRole() != 0 || !user.getDepartmentId().equals(departmentId)) {
            return Result.error("无权审批该学生");
        }
        userService.approveUser(userId, status);
        return Result.successMsg(status == 1 ? "审批通过" : "审批拒绝");
    }
}
