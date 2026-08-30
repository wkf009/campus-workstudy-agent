package com.workstudy.common;

/**
 * 业务访问拒绝异常。
 * 由 RoleCheckAspect 在角色校验失败时抛出，GlobalExceptionHandler 统一转换为 403 响应。
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
