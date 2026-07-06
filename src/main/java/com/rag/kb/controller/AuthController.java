package com.rag.kb.controller;

import com.rag.kb.model.dto.*;
import com.rag.kb.model.entity.User;
import com.rag.kb.service.CaptchaService;
import com.rag.kb.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final CaptchaService captchaService;

    @GetMapping({"/", "/login"})
    public String loginPage(HttpSession session) {
        if (session.getAttribute("userId") != null) {
            return "redirect:/kb/list";
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(HttpSession session) {
        if (session.getAttribute("userId") != null) {
            return "redirect:/kb/list";
        }
        return "register";
    }

    @PostMapping("/login")
    public String loginForm(@RequestParam String username,
                            @RequestParam String password,
                            @RequestParam String captcha,
                            HttpSession session) {
        // 校验验证码
        if (!captchaService.validate(session, captcha)) {
            return "redirect:/login?error=" + URLEncoder.encode("验证码错误或已过期", StandardCharsets.UTF_8);
        }
        try {
            User user = userService.login(username, password);
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            return "redirect:/kb/list";
        } catch (Exception e) {
            return "redirect:/login?error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/api/auth/login")
    @ResponseBody
    public ApiResult<User> login(@RequestBody LoginDto dto, HttpSession session) {
        try {
            User user = userService.login(dto.getUsername(), dto.getPassword());
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            return ApiResult.success(user);
        } catch (Exception e) {
            return ApiResult.error(401, e.getMessage());
        }
    }

    @PostMapping("/api/auth/register")
    @ResponseBody
    public ApiResult<User> register(@RequestBody RegisterDto dto, HttpSession session) {
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            return ApiResult.error(400, "两次密码不一致");
        }
        try {
            User user = userService.register(dto.getUsername(), dto.getPassword());
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            return ApiResult.success(user);
        } catch (Exception e) {
            return ApiResult.error(400, e.getMessage());
        }
    }

    @GetMapping("/api/auth/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
