package com.joykit.modular.auth.dto;

import com.joykit.modular.auth.service.AuthService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "登录参数")
public record LoginDTO(@NotBlank @Size(max = 100) String account,
                       @NotBlank @Size(max = 256) String password,
                       AuthService.ClientType clientType) {}
