package com.workstudy.filter;

import com.workstudy.utils.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT 认证过滤器：解析 Authorization 头中的 Bearer token，
 * 将 userId 写入 request attribute 并注入 Spring Security 上下文。
 * 注意：不打印 token 明文，防止日志泄露认证凭证。
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                Long userId = jwtUtils.getUserIdFromToken(token);
                Integer role = jwtUtils.getRoleFromToken(token);

                request.setAttribute("userId", userId);

                String roleName = getRoleName(role);
                UserDetails userDetails = User.builder()
                        .username(userId.toString())
                        .password("")
                        .authorities(new String[]{roleName})
                        .build();

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JWT 认证成功: userId={}, role={}", userId, roleName);
            } catch (Exception e) {
                // token 无效/过期：清空上下文，由 Security 层统一返回 401
                log.debug("JWT 解析失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }

    private String getRoleName(Integer role) {
        if (role == null) {
            return "ROLE_STUDENT";
        }
        switch (role) {
            case 0:
                return "ROLE_STUDENT";
            case 1:
                return "ROLE_MENTOR";
            case 2:
                return "ROLE_DEPARTMENT";
            case 3:
                return "ROLE_ADMIN";
            default:
                return "ROLE_STUDENT";
        }
    }
}
