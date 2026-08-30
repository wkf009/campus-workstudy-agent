package com.workstudy.controller;

import com.workstudy.common.Result;
import com.workstudy.entity.User;
import com.workstudy.service.UserService;
import com.workstudy.utils.JwtUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtils jwtUtils;

    public AuthController(UserService userService, JwtUtils jwtUtils) {
        this.userService = userService;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        User user = userService.findByUsername(username).orElse(null);
        if (user == null || !userService.checkPassword(password, user.getPassword())) {
            return Result.error("用户名或密码错误");
        }

        if (user.getStatus() == 0) {
            return Result.error("账号尚未审批，请联系管理员");
        } else if (user.getStatus() == 2) {
            return Result.error("账号已被拒绝，无法登录");
        }

        String token = jwtUtils.generateToken(user.getId(), user.getRole());
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", user);
        return Result.success(data);
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody User user) {
        userService.register(user);
        return Result.successMsg("注册成功");
    }
}
