package com.aiinterviewer.controller;

import com.aiinterviewer.common.CaptchaService;
import com.aiinterviewer.common.JwtUtil;
import com.aiinterviewer.common.Result;
import com.aiinterviewer.dto.resp.UserResp;
import com.aiinterviewer.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户认证接口
 * <p>
 * <ul>
 *   <li>GET  /api/auth/captcha       获取图形验证码</li>
 *   <li>POST /api/auth/register      注册</li>
 *   <li>POST /api/auth/login         登录</li>
 *   <li>POST /api/auth/logout        登出</li>
 *   <li>GET  /api/auth/me            获取当前用户信息</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final JwtUtil jwtUtil;

    @GetMapping("/captcha")
    public Result<Map<String, String>> captcha() {
        CaptchaService.CaptchaResult result = captchaService.generate();
        return Result.ok(Map.of("token", result.token(), "image", result.imageBase64()));
    }

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body,
                                                 HttpServletResponse response) {
        String username = body.get("username");
        String password = body.get("password");
        String captchaToken = body.get("captchaToken");
        String captchaCode = body.get("captchaCode");

        if (username == null || password == null || captchaToken == null || captchaCode == null) {
            return Result.fail(400, "参数不完整");
        }

        String token = authService.register(username.trim(), password, captchaToken, captchaCode.trim());

        // 设置 httpOnly Cookie
        Cookie cookie = new Cookie("auth_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60); // 24 小时
        cookie.setAttribute("SameSite", "None");
        response.addCookie(cookie);

        // 返回 token 和用户信息
        UserResp user = authService.getCurrentUser();
        return Result.ok(Map.of("token", token, "user", user));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                              HttpServletResponse response) {
        String username = body.get("username");
        String password = body.get("password");
        String captchaToken = body.get("captchaToken");
        String captchaCode = body.get("captchaCode");

        if (username == null || password == null || captchaToken == null || captchaCode == null) {
            return Result.fail(400, "参数不完整");
        }

        String token = authService.login(username.trim(), password, captchaToken, captchaCode.trim());

        // 设置 httpOnly Cookie
        Cookie cookie = new Cookie("auth_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60); // 24 小时
        cookie.setAttribute("SameSite", "None");
        response.addCookie(cookie);

        // 同时返回 token 给前端（用于 axios 拦截或备用）
        UserResp user = authService.getCurrentUser();
        return Result.ok(Map.of("token", token, "user", user));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("auth_token", "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "None");
        response.addCookie(cookie);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<UserResp> me() {
        return Result.ok(authService.getCurrentUser());
    }
}
