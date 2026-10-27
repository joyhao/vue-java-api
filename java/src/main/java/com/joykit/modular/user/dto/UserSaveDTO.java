package com.joykit.modular.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "新增或修改用户资料")
public class UserSaveDTO {
    @NotBlank(message = "请输入姓名")
    @Size(max = 255, message = "姓名最多 255 字")
    private String name;
    @Size(max = 2048)
    @Pattern(regexp = "(?i)^https?://\\S+$", message = "头像必须为 HTTP 或 HTTPS 地址")
    private String avatarUrl;
    @Email(message = "请输入正确的邮箱")
    @Size(max = 254)
    private String email;
    @Size(max = 32)
    @Pattern(regexp = "^\\+?[0-9()\\-\\s]{3,32}$", message = "请输入有效的联系电话")
    private String phone;
    @NotNull
    @Min(0)
    @Max(1)
    private Integer status;
}
