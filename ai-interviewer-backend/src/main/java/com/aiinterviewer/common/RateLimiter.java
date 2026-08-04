package com.aiinterviewer.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单的滑动窗口速率限制器（内存实现）。
 * <p>
 * 用于限制用户对敏感接口的访问频率，防止滥用。
 * 每个用户每 {windowMs} 毫秒内最多允许 {maxRequests} 次请求。
 */
public class RateLimiter {

    private final int maxRequests;
    private final long windowMs;
    private final Map<Long, Window> windows = new ConcurrentHashMap<>();

    /**
     * @param maxRequests 时间窗口内允许的最大请求数
     * @param windowMs    时间窗口大小（毫秒）
     */
    public RateLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    /**
     * 检查指定用户是否允许继续请求。
     *
     * @param userId 用户 ID
     * @return true 如果允许请求，false 如果已超限
     */
    public boolean tryAcquire(Long userId) {
        long now = System.currentTimeMillis();
        Window w = windows.computeIfAbsent(userId, k -> new Window(now));
        synchronized (w) {
            if (now - w.start >= windowMs) {
                // 滑动窗口过限，重置
                w.start = now;
                w.count.set(0);
            }
            if (w.count.get() >= maxRequests) {
                return false;
            }
            w.count.incrementAndGet();
            return true;
        }
    }

    /** 清理过期窗口（定期调用，防止内存泄漏） */
    public void cleanUp() {
        long now = System.currentTimeMillis();
        windows.values().removeIf(w -> {
            synchronized (w) {
                return now - w.start >= windowMs;
            }
        });
    }

    private static class Window {
        long start;
        AtomicInteger count = new AtomicInteger(0);

        Window(long start) {
            this.start = start;
        }
    }
}
