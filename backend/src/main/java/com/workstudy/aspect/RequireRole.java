package com.workstudy.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级角色鉴权注解。
 * 与 RoleCheckAspect 配合使用：标注的接口仅允许指定角色访问。
 * 角色：0-学生, 1-企业导师, 2-部门管理员, 3-超级管理员
 *
 * 示例：
 *   @RequireRole({3})          // 仅超管
 *   @RequireRole({1, 2})       // 导师或部门管理员
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
    /** 允许访问的角色列表 */
    int[] value();
}
