package com.aiinterviewer.common;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 验证码存储（内存版）
 * <p>
 * 存储验证码 token → 正确文本 的映射，5 分钟后自动过期清理。
 * 生产环境建议替换为 Redis，当前 MVP 使用内存足以满足单机部署。
 */
@Slf4j
@Component
public class CaptchaStore {

    private static final long EXPIRATION_MS = 5 * 60 * 1000L; // 5 分钟
    private static final int CLEAN_INTERVAL_SEC = 60;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 4;

    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @PostConstruct
    public void init() {
        // 定时清理过期验证码
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "captcha-cleaner");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::cleanExpired, CLEAN_INTERVAL_SEC, CLEAN_INTERVAL_SEC, TimeUnit.SECONDS);
        log.info("[captcha] 验证码存储已初始化，清理间隔 {}s", CLEAN_INTERVAL_SEC);
    }

    /** 生成验证码，返回 token 和验证码文本 */
    public CaptchaResult create() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        String code = sb.toString();
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        CaptchaEntry entry = new CaptchaEntry(code, System.currentTimeMillis());
        store.put(token, entry);
        log.debug("[captcha] 生成验证码: token={}, code={}", token, code);
        return new CaptchaResult(token, code);
    }

    /** 验证码验证：验证成功后删除该 token，防止重复使用 */
    public boolean validate(String token, String code) {
        if (token == null || code == null) return false;
        CaptchaEntry entry = store.remove(token);
        if (entry == null) return false;
        if (System.currentTimeMillis() - entry.createdAt > EXPIRATION_MS) return false;
        return entry.code.equalsIgnoreCase(code.trim());
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now - e.getValue().createdAt > EXPIRATION_MS);
    }

    /** 仅用于内部存储 */
    private record CaptchaEntry(String code, long createdAt) {
    }

    /** 对外暴露的验证码信息 */
    public record CaptchaResult(String token, String code) {
    }
}
