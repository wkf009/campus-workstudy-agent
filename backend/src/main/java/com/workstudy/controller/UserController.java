package com.workstudy.controller;

import com.workstudy.aspect.LogOperation;
import com.workstudy.aspect.RequireRole;
import com.workstudy.common.Result;
import com.workstudy.entity.User;
import com.workstudy.service.UserService;
import com.workstudy.utils.JwtUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class UserController {

    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @RequireRole({3})
    @GetMapping("/users")
    public Result<List<User>> getAllUsers() {
        return Result.success(userService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public Result<User> getUserById(@PathVariable Long id) {
        User user = userService.selectById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(user);
    }

    @RequireRole({3})
    @GetMapping("/users/role/{role}")
    public Result<List<User>> getUsersByRole(@PathVariable Integer role) {
        return Result.success(userService.getUsersByRole(role));
    }

    @LogOperation
    @RequireRole({3})
    @PostMapping("/users")
    public Result<User> createUser(@RequestBody User user) {
        User created = userService.register(user);
        return Result.success("创建成功", created);
    }

    @LogOperation
    @PutMapping("/users/{id}")
    public Result<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        Long currentUserId = JwtUtils.getUserIdFromRequest(JwtUtils.getRequest());
        User currentUser = userService.selectById(currentUserId);
        if (currentUser == null) {
            return Result.error("当前用户不存在");
        }
        User targetUser = userService.selectById(id);
        if (targetUser == null) {
            return Result.error("目标用户不存在");
        }

        if (currentUser.getRole() == 3) {
            if (currentUserId.equals(id)) {
                return Result.error("不能编辑自己的账号");
            }
        } else if (currentUser.getRole() == 2 || currentUser.getRole() == 1) {
            if (!currentUserId.equals(id) && !(targetUser.getDepartmentId() != null
                    && targetUser.getDepartmentId().equals(currentUser.getDepartmentId())
                    && targetUser.getRole() == 0)) {
                return Result.error("没有权限编辑该用户");
            }
        } else if (currentUser.getRole() == 0) {
            if (!currentUserId.equals(id)) {
                return Result.error("学生只能编辑自己的信息");
            }
        }

        targetUser.setUsername(user.getUsername());
        targetUser.setRealName(user.getRealName());
        targetUser.setPhone(user.getPhone());
        targetUser.setEmail(user.getEmail());
        if (user.getRole() != null) targetUser.setRole(user.getRole());
        if (user.getDepartmentId() != null) targetUser.setDepartmentId(user.getDepartmentId());
        if (user.getStudentNo() != null) targetUser.setStudentNo(user.getStudentNo());
        if (user.getMajor() != null) targetUser.setMajor(user.getMajor());
        if (user.getStatus() != null) targetUser.setStatus(user.getStatus());
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            targetUser.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        userService.update(targetUser);
        return Result.success("更新成功", targetUser);
    }

    @LogOperation
    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        Long currentUserId = JwtUtils.getUserIdFromRequest(JwtUtils.getRequest());
        User currentUser = userService.selectById(currentUserId);
        if (currentUser == null) {
            return Result.error("当前用户不存在");
        }
        User targetUser = userService.selectById(id);
        if (targetUser == null) {
            return Result.error("目标用户不存在");
        }

        if (currentUser.getRole() == 3) {
            if (currentUserId.equals(id)) {
                return Result.error("不能删除自己的账号");
            }
        } else if (currentUser.getRole() == 2 || currentUser.getRole() == 1) {
            if (!currentUserId.equals(id) && !(targetUser.getDepartmentId() != null
                    && targetUser.getDepartmentId().equals(currentUser.getDepartmentId())
                    && targetUser.getRole() == 0)) {
                return Result.error("没有权限删除该用户");
            }
        } else if (currentUser.getRole() == 0) {
            if (!currentUserId.equals(id)) {
                return Result.error("学生只能注销自己的账号");
            }
        }

        userService.deleteById(id);
        return Result.successMsg("删除成功");
    }
}
