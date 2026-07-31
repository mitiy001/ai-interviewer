package com.aiinterviewer.service;

import com.aiinterviewer.dto.resp.UserResp;

/**
 * 用户认证 Service
 */
public interface AuthService {

    /** 用户注册，返回 JWT */
    String register(String username, String password, String captchaToken, String captchaCode);

    /** 用户登录，返回 JWT */
    String login(String username, String password, String captchaToken, String captchaCode);

    /** 获取当前登录用户信息 */
    UserResp getCurrentUser();
}
