package com.rag.kb.controller;

import com.rag.kb.model.dto.*;
import com.rag.kb.model.entity.User;
import com.rag.kb.service.CaptchaService;
import com.rag.kb.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class AuthController {

    private final UserService userService;
    private final CaptchaService captchaService;

    @PostMapping("/api/auth/login")
    @ResponseBody
    public ApiResult<User> login(@RequestBody LoginDto dto, HttpSession session) {
        if (!captchaService.validate(session, dto.getCaptcha())) {
            return ApiResult.error(400, "验证码错误或已过期");
        }
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
        if (!captchaService.validate(session, dto.getCaptcha())) {
            return ApiResult.error(400, "验证码错误或已过期");
        }
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
    @ResponseBody
    public ApiResult<Void> logout(HttpSession session) {
        session.invalidate();
        return ApiResult.success(null);
    }
}
