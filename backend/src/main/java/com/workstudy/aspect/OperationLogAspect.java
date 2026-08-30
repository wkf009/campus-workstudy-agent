package com.workstudy.aspect;

import com.workstudy.entity.OperationLog;
import com.workstudy.utils.JwtUtils;
import com.workstudy.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;

    public OperationLogAspect(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Around("@annotation(com.workstudy.aspect.LogOperation)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Object result = point.proceed();
        try {
            saveLog(point);
        } catch (Exception e) {
            // 日志记录失败不影响业务
        }
        return result;
    }

    private void saveLog(ProceedingJoinPoint point) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return;

        HttpServletRequest request = attributes.getRequest();
        OperationLog log = new OperationLog();

        try {
            Long userId = JwtUtils.getUserIdFromRequest(request);
            log.setUserId(userId);
        } catch (Exception e) {
            log.setUserId(null);
        }

        MethodSignature signature = (MethodSignature) point.getSignature();
        log.setMethod(signature.getDeclaringTypeName() + "." + signature.getName());
        log.setOperation(signature.getName());

        Object[] args = point.getArgs();
        StringBuilder params = new StringBuilder();
        for (Object arg : args) {
            if (arg != null && !(arg instanceof HttpServletRequest)) {
                if (params.length() > 0) params.append(", ");
                String str = arg.toString();
                params.append(str.length() > 200 ? str.substring(0, 200) + "..." : str);
            }
        }
        log.setParams(params.toString());
        log.setIp(getIpAddress(request));
        log.setCreateTime(LocalDateTime.now());

        operationLogMapper.insert(log);
    }

    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null && ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }
}
