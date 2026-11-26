package com.joykit.modular.auth.controller;

import com.joykit.common.result.Result;
import com.joykit.modular.auth.dto.LoginDTO;
import com.joykit.modular.auth.dto.RegisterDTO;
import com.joykit.modular.auth.service.AuthService;
import com.joykit.modular.auth.service.LoginRateLimiter;
import com.joykit.modular.auth.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@Tag(name = "认证管理")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final LoginRateLimiter rateLimiter;

    @Operation(summary = "注册账号")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<Boolean> register(@Valid @RequestBody RegisterDTO body, HttpServletRequest request) {
        // 注册是匿名接口且每次要跑 PBKDF2，先做 IP 频率限制
        rateLimiter.checkRegisterAllowed(clientIp(request));
        if (!body.password().equals(body.confirmPassword())) {
            throw new com.joykit.common.exception.BusinessException(
                    com.joykit.common.exception.BusinessException.Kind.VALIDATION, "两次密码不一致");
        }
        try {
            auth.register(body.account(), body.password());
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            Throwable cause = exception.getMostSpecificCause();
            if (cause.getMessage() != null && cause.getMessage().contains("user_account_unique")) {
                throw new com.joykit.common.exception.BusinessException(
                        com.joykit.common.exception.BusinessException.Kind.CONFLICT, "账号已存在");
            }
            throw exception;
        }
        return Result.success(true);
    }

    @Operation(summary = "账号登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO body, HttpServletRequest request,
                                 HttpServletResponse response) {
        var type = body.clientType() == null ? AuthService.ClientType.WEB : body.clientType();
        // 登录是匿名接口且每次要跑 PBKDF2：先限流（账号失败锁定 + IP 窗口），再进业务
        rateLimiter.checkLoginAllowed(clientIp(request), body.account());
        var login = auth.login(body.account(), body.password(), type);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (login == null) {
            rateLimiter.recordLoginFailure(clientIp(request), body.account());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return Result.fail(401, "账号或密码错误，或账号已停用", null);
        }
        rateLimiter.recordLoginSuccess(body.account());
        return loginResult(login, request, response);
    }

    public record RefreshDTO(@jakarta.validation.constraints.Size(max = 4096) String refreshToken) {}

    @Operation(summary = "刷新登录凭证")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody(required = false) RefreshDTO body,
                                   HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = body != null && body.refreshToken() != null ? body.refreshToken() : refreshCookie(request);
        var login = auth.refresh(refreshToken);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (login == null) {
            response.setStatus(401);
            return Result.fail(401, "登录已失效，请重新登录", null);
        }
        return loginResult(login, request, response);
    }

    private Result<LoginVO> loginResult(AuthService.Login login, HttpServletRequest request, HttpServletResponse response) {
        boolean web = "WEB".equals(login.user().clientType());
        if (web) {
            if (login.refreshToken() != null) response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieHeader(login.refreshToken(),
                    Math.max(0, java.time.Duration.between(java.time.Instant.now(), login.refreshExpiresAt()).getSeconds()), request));
        }
        return Result.success(new LoginVO(login.user(), login.token(), login.expiresAt(),
                web ? null : login.refreshToken(), login.refreshExpiresAt()));
    }

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    public Result<AuthService.Identity> me(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        var user = auth.authenticate(token(request));
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return Result.fail(401, "登录已过期或已在其他设备登录", null);
        }
        return Result.success(user);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Boolean> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(token(request));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieHeader("", 0, request));
        return Result.success(true);
    }

    public static String token(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) return authorization.substring(7);
        return null;
    }

    /**
     * 只用 TCP 对端地址。不信任 X-Forwarded-For（客户端可伪造）；
     * 部署在反向代理后面时应配置 server.forward-headers-strategy，让 servlet 容器以受信方式解析该头。
     */
    static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private static String refreshCookie(HttpServletRequest request) {
        if (request.getCookies() != null) for (var cookie : request.getCookies())
            if ("joykit_refresh".equals(cookie.getName())) return cookie.getValue();
        return null;
    }

    private static String refreshCookieHeader(String token, long maxAge, HttpServletRequest request) {
        return ResponseCookie.from("joykit_refresh", token).httpOnly(true).secure(request.isSecure())
                .sameSite("Strict").path("/api/auth").maxAge(maxAge).build().toString();
    }
}
