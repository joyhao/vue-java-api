package com.joykit.modular.auth.controller;

import com.joykit.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    public static final String SESSION_USER = "authenticatedUser";
    private final String account;
    private final String password;

    public AuthController(@Value("${APP_AUTH_ACCOUNT:}") String account,
                          @Value("${APP_AUTH_PASSWORD:}") String password) {
        this.account = account;
        this.password = password;
    }

    public record LoginRequest(@NotBlank @Size(max = 100) String account,
                               @NotBlank @Size(max = 256) String password) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        if (account.isBlank() || password.isBlank()) {
            return ResponseEntity.status(503).body(Result.fail(503, "管理员尚未配置登录账号，请设置 APP_AUTH_ACCOUNT 和 APP_AUTH_PASSWORD", null));
        }
        boolean accountMatches = MessageDigest.isEqual(account.getBytes(StandardCharsets.UTF_8), body.account().getBytes(StandardCharsets.UTF_8));
        boolean passwordMatches = MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), body.password().getBytes(StandardCharsets.UTF_8));
        if (!accountMatches || !passwordMatches) {
            return ResponseEntity.status(401).body(Result.fail(401, "账号或密码错误", null));
        }
        var session = request.getSession();
        request.changeSessionId();
        session.setMaxInactiveInterval(1800);
        session.setAttribute(SESSION_USER, account);
        return ResponseEntity.ok(Result.success(Map.of("account", account)));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        var session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute(SESSION_USER);
        if (user == null) return ResponseEntity.status(401).body(Result.fail(401, "请先登录", null));
        return ResponseEntity.ok(Result.success(Map.of("account", user)));
    }

    @PostMapping("/logout")
    public Result<Boolean> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        return Result.success(true);
    }
}
