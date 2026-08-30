package com.workstudy.controller;

import com.workstudy.aspect.LogOperation;
import com.workstudy.aspect.RequireRole;
import com.workstudy.common.Result;
import com.workstudy.entity.User;
import com.workstudy.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class UserApprovalController {

    private final UserService userService;

    public UserApprovalController(UserService userService) {
        this.userService = userService;
    }

    @RequireRole({3})
    @GetMapping("/users/pending")
    public Result<List<User>> getPendingUsers() {
        return Result.success(userService.getPendingUsers());
    }

    @LogOperation
    @RequireRole({3})
    @PostMapping("/users/approve")
    public Result<Void> approveUser(@RequestBody java.util.Map<String, Object> request) {
        Long userId = Long.parseLong(request.get("userId").toString());
        Integer status = Integer.parseInt(request.get("status").toString());
        userService.approveUser(userId, status);
        return Result.successMsg(status == 1 ? "审批通过" : "审批拒绝");
    }
}
