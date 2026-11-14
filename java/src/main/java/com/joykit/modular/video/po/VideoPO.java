package com.joykit.modular.video.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("video")
public class VideoPO {
    @TableId(type = IdType.INPUT)
    private String id;
    private String name;
    @TableField("relative_path")
    private String relativePath;
    @TableField("file_size")
    private Long fileSize;
    @TableField("duration_seconds")
    private Double durationSeconds;
    @TableField(value = "created_at", updateStrategy = FieldStrategy.NEVER)
    private Instant createdAt;
    @TableField("updated_at")
    private Instant updatedAt;
    @TableField("synced_at")
    private Instant syncedAt;
}
