package com.aiinterviewer.common;

/**
 * 当前用户上下文（线程级）
 * <p>
 * 由 AuthFilter 在每个 HTTP 请求开始时从 JWT Cookie 解析 userId 并设置，
 * 请求结束时由 AuthFilter 清理。所有 Service 通过 {@link #getUserId()} 获取当前登录用户。
 * <p>
 * 未登录的公开接口（如登录/注册/验证码）请求中 userId 为 null，需自行允许。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /** 设置当前用户 ID（由 AuthFilter 调用） */
    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    /** 获取当前用户 ID */
    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    /** 清理上下文（由 AuthFilter 在请求结束时调用） */
    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}
