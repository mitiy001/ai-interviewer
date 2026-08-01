package com.aiinterviewer.service;

import com.aiinterviewer.dto.resp.UserResp;

import java.util.List;
import java.util.Map;

/**
 * 用户认证 Service
 */
public interface AuthService {

    /** 用户登录，返回 JWT */
    String login(String username, String password);

    /** 管理员创建用户，返回 JWT（仅 admin 可调用） */
    String createUser(String username, String password);

    /** 获取当前登录用户信息 */
    UserResp getCurrentUser();

    // ===== 管理员接口 =====

    /** 获取所有用户列表 */
    List<UserResp> listUsers();

    /** 更新用户信息（状态、角色） */
    void updateUser(Long id, Map<String, Object> body);

    /** 删除用户 */
    void deleteUser(Long id);

    /** 重置用户密码 */
    void resetPassword(Long id, String newPassword);
}