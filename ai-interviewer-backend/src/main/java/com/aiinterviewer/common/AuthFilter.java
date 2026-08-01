package com.aiinterviewer.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * JWT 认证过滤器
 * <p>
 * 从 httpOnly Cookie 中读取 JWT Token，解析 userId 后设置到 UserContext。
 * 公开接口（登录/注册/验证码）无需认证，直接放行。
 * 认证失败时返回 401 JSON，不重定向（前端 Axios 拦截器处理跳转）。
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "auth_token";

    /** 公开接口（无需认证） */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/actuator/health"
    );

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // OPTIONS 请求（CORS 预检）直接放行
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 公开接口直接放行
        if (PUBLIC_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 从 Cookie 中提取 JWT
        String token = extractToken(request);
        if (token != null && jwtUtil.validateToken(token)) {
            Long userId = jwtUtil.getUserId(token);
            if (userId != null) {
                UserContext.setUserId(userId);
                try {
                    filterChain.doFilter(request, response);
                } finally {
                    UserContext.clear();
                }
                return;
            }
        }

        // 未认证 → 返回 401
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期，请重新登录\"}");
    }

    private String extractToken(HttpServletRequest request) {
        // 1. 优先从 httpOnly Cookie 中提取（常规 Ajax 请求）
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE_NAME.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        // 2. 从查询参数 token 中提取（SSE EventSource 跨域场景，Cookie 可能被浏览器拦截）
        String tokenParam = request.getParameter("token");
        if (tokenParam != null && !tokenParam.isEmpty()) {
            return tokenParam;
        }
        return null;
    }
}