package com.joykit.modular.auth.vo;

import com.joykit.modular.auth.service.AuthService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "登录结果，网页端不返回原始Token")
public record LoginVO(AuthService.Identity user, String token, Instant expiresAt, String refreshToken, Instant refreshExpiresAt) {}
