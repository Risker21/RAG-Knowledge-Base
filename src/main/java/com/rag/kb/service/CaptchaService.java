package com.rag.kb.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CaptchaService {

    private static final int WIDTH = 140;
    private static final int HEIGHT = 48;
    private static final int LENGTH = 4;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String SESSION_KEY = "captcha_code";
    private static final String SESSION_TIME = "captcha_time";

    /**
     * 生成验证码图片，将答案存入 Session
     * @return 图片字节数组 (PNG)
     */
    public byte[] generateImage(HttpSession session) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }

        // 存储到 Session（带 5 分钟过期校验）
        session.setAttribute(SESSION_KEY, code.toString());
        session.setAttribute(SESSION_TIME, System.currentTimeMillis());

        return drawImage(code.toString());
    }

    /**
     * 验证用户输入的验证码
     */
    public boolean validate(HttpSession session, String userInput) {
        if (userInput == null || userInput.isBlank()) return false;

        String expected = (String) session.getAttribute(SESSION_KEY);
        Long genTime = (Long) session.getAttribute(SESSION_TIME);

        // 没有验证码
        if (expected == null || genTime == null) return false;

        // 超时 5 分钟
        if (System.currentTimeMillis() - genTime > 5 * 60 * 1000) return false;

        // 无论对错，用完即销毁（防止重复使用）
        session.removeAttribute(SESSION_KEY);
        session.removeAttribute(SESSION_TIME);

        return expected.equalsIgnoreCase(userInput.trim());
    }

    private byte[] drawImage(String code) {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // 抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 背景
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 噪点
        g.setColor(new Color(230, 230, 230));
        for (int i = 0; i < 80; i++) {
            int x = RANDOM.nextInt(WIDTH);
            int y = RANDOM.nextInt(HEIGHT);
            g.fillRect(x, y, 2, 2);
        }

        // 干扰线
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < 3; i++) {
            g.setColor(new Color(
                    180 + RANDOM.nextInt(60),
                    180 + RANDOM.nextInt(60),
                    180 + RANDOM.nextInt(60)));
            int x1 = RANDOM.nextInt(WIDTH / 2);
            int y1 = RANDOM.nextInt(HEIGHT);
            int x2 = WIDTH / 2 + RANDOM.nextInt(WIDTH / 2);
            int y2 = RANDOM.nextInt(HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }

        // 字符（每个字符随机旋转+颜色）
        Font font = new Font("Arial", Font.BOLD, 28);
        g.setFont(font);
        char[] chars = code.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            double angle = (RANDOM.nextDouble() - 0.5) * 0.5;
            int color = 30 + RANDOM.nextInt(100);
            g.setColor(new Color(color, color / 2, 160 - RANDOM.nextInt(40)));

            AffineTransform orig = g.getTransform();
            g.rotate(angle, 30 + i * 28, HEIGHT / 2.0);
            g.drawString(String.valueOf(chars[i]), 20 + i * 28, 34 + RANDOM.nextInt(4));
            g.setTransform(orig);
        }

        g.dispose();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("验证码生成失败", e);
        }
    }
}
