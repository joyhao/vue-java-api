package com.joykit.modular.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "注册参数")
public record RegisterDTO(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{3,31}", message = "账号须以字母开头，由4至32位字母、数字或下划线组成") String account,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank String confirmPassword) {}
