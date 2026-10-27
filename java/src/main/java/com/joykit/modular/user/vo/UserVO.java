package com.joykit.modular.user.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Data;

@Data
@Schema(description = "用户展示信息")
public class UserVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String avatarUrl;
    private String email;
    private String phone;
    private Integer status;
    private Instant createdAt;
    private Instant updatedAt;
}
