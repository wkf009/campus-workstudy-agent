package com.workstudy.aspect;

import com.workstudy.common.AccessDeniedException;
import com.workstudy.entity.User;
import com.workstudy.service.UserService;
import com.workstudy.utils.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * @RequireRole 注解的 AOP 实现：方法执行前校验当前登录用户角色。
 * 角色不匹配时抛 AccessDeniedException，由 GlobalExceptionHandler 统一返回 403。
 *
 * 说明：这里通过 userId 重新查库获取角色，而不是直接信任 JWT 中的 role 声明，
 * 避免角色变更（如账号降级/禁用）后 token 仍旧有效造成的越权。
 */
@Aspect
@Component
public class RoleCheckAspect {

    private final UserService userService;

    public RoleCheckAspect(UserService userService) {
        this.userService = userService;
    }

    @Around("@annotation(requireRole)")
    public Object checkRole(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
        HttpServletRequest request = JwtUtils.getRequest();
        if (request == null) {
            throw new AccessDeniedException("无法获取请求上下文");
        }

        Long userId = JwtUtils.getUserIdFromRequest(request);
        User user = userService.selectById(userId);
        if (user == null) {
            throw new AccessDeniedException("用户不存在");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new AccessDeniedException("账号已被禁用，无法操作");
        }

        int role = user.getRole() == null ? -1 : user.getRole();
        for (int allowed : requireRole.value()) {
            if (allowed == role) {
                return joinPoint.proceed();
            }
        }
        throw new AccessDeniedException("没有操作权限");
    }
}
