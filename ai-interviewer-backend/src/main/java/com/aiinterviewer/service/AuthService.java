package com.aiinterviewer.service;

import com.aiinterviewer.dto.resp.UserResp;

/**
 * 用户认证 Service
 */
public interface AuthService {

    /** 用户登录，返回 JWT */
    String login(String username, String password, String captchaToken, String captchaCode);

    /** 管理员创建用户，返回 JWT（仅 admin 可调用） */
    String createUser(String username, String password);

    /** 获取当前登录用户信息 */
    UserResp getCurrentUser();
}