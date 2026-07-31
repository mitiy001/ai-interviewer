package com.aiinterviewer.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * 图形验证码服务
 * <p>
 * 生成验证码图片（Base64 PNG），依赖 CaptchaStore 存储验证。
 * 使用 Java AWT 在内存中绘制随机背景色 + 干扰线 + 弧线 + 噪点 + 扭曲文字的验证码图片。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 65;
    private static final int FONT_SIZE = 32;
    /** 字符数量由 CaptchaStore.CODE_LENGTH 决定，这里仅做安全兜底 */
    private static final int LINE_COUNT = 8;
    private static final int ARC_COUNT = 4;
    private static final int NOISE_COUNT = 250;

    private final CaptchaStore captchaStore;

    private final java.util.Random random = new java.util.Random();

    /** 生成验证码，返回 Base64 图片和 token */
    public CaptchaResult generate() {
        CaptchaStore.CaptchaResult result = captchaStore.create();
        String base64Image = generateImageBase64(result.code());
        return new CaptchaResult(result.token(), base64Image);
    }

    /** 验证验证码 */
    public boolean validate(String token, String code) {
        return captchaStore.validate(token, code);
    }

    /** 生成验证码图片的 Base64 编码（data:image/png;base64,...） */
    private String generateImageBase64(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        try {
            // 抗锯齿
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 1. 随机背景色（浅色系）
            int bgR = 230 + random.nextInt(26);   // 230-255
            int bgG = 230 + random.nextInt(26);
            int bgB = 230 + random.nextInt(26);
            g.setColor(new Color(bgR, bgG, bgB));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            // 2. 干扰线（随机颜色、粗细、半透明）
            for (int i = 0; i < LINE_COUNT; i++) {
                int r = 100 + random.nextInt(156);
                int gr = 100 + random.nextInt(156);
                int b = 100 + random.nextInt(156);
                g.setColor(new Color(r, gr, b, 120 + random.nextInt(80)));
                g.setStroke(new BasicStroke(1.5f + random.nextFloat() * 2f));
                int x1 = random.nextInt(WIDTH);
                int y1 = random.nextInt(HEIGHT);
                int x2 = random.nextInt(WIDTH);
                int y2 = random.nextInt(HEIGHT);
                g.drawLine(x1, y1, x2, y2);
            }

            // 3. 干扰弧线（随机颜色、半透明）
            for (int i = 0; i < ARC_COUNT; i++) {
                int r = 100 + random.nextInt(156);
                int gr = 100 + random.nextInt(156);
                int b = 100 + random.nextInt(156);
                g.setColor(new Color(r, gr, b, 80 + random.nextInt(60)));
                g.setStroke(new BasicStroke(2f));
                int x = random.nextInt(WIDTH - 40) + 20;
                int y = random.nextInt(HEIGHT - 20) + 10;
                int w = 30 + random.nextInt(60);
                int h = 20 + random.nextInt(40);
                int startAngle = random.nextInt(360);
                int arcAngle = 60 + random.nextInt(120);
                g.drawArc(x, y, w, h, startAngle, arcAngle);
            }

            // 4. 绘制文字（每个字符独立旋转、随机颜色、随机垂直偏移）
            char[] chars = code.toCharArray();
            int charWidth = WIDTH / (chars.length + 1);
            // 使用更粗的字体组合
            g.setFont(new Font("Arial", Font.BOLD, FONT_SIZE));
            for (int i = 0; i < chars.length; i++) {
                // 每个字符随机颜色（深色系，确保可读性）
                int cr = 20 + random.nextInt(80);
                int cg = 20 + random.nextInt(80);
                int cb = 60 + random.nextInt(120);
                g.setColor(new Color(cr, cg, cb));

                // 独立旋转（±0.35 rad）
                double angle = (random.nextDouble() - 0.5) * 0.7;
                int cx = charWidth * (i + 1);
                int cy = HEIGHT / 2 + random.nextInt(10) - 5; // 垂直偏移
                g.rotate(angle, cx, cy);
                g.drawString(String.valueOf(chars[i]), cx - 10, cy + 10);
                g.rotate(-angle, cx, cy);
            }

            // 5. 噪点（随机颜色、大小、位置）
            for (int i = 0; i < NOISE_COUNT; i++) {
                int r = 80 + random.nextInt(176);
                int gr = 80 + random.nextInt(176);
                int b = 80 + random.nextInt(176);
                g.setColor(new Color(r, gr, b, 100 + random.nextInt(100)));
                int size = 1 + random.nextInt(3);
                int x = random.nextInt(WIDTH);
                int y = random.nextInt(HEIGHT);
                g.fillRect(x, y, size, size);
            }
        } finally {
            g.dispose();
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("[captcha] 生成图片失败", e);
            return "";
        }
    }

    /** 验证码对外返回结果 */
    public record CaptchaResult(String token, String imageBase64) {
    }
}
