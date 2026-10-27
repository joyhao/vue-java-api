package com.joykit.modular.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UserClearDTO(@NotBlank String confirmation) {
}
