package com.joykit.modular.user.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("\"user\"")
public class UserPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    @TableField(value = "avatar_url", updateStrategy = FieldStrategy.ALWAYS)
    private String avatarUrl;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;
    private Integer status;
    @TableField(value = "created_at", updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;
}
