package com.joykit.modular.video.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "视频文件信息")
public record VideoVO(
        @Schema(description = "视频 ID") String id,
        @Schema(description = "文件名称") String name,
        @Schema(description = "文件大小（字节）") long size,
        @Schema(description = "视频时长（秒）；尚未探测时为 null") Double durationSeconds,
        @Schema(description = "缩略图 HTTP 地址") String thumbnailUrl,
        @Schema(description = "Nginx 静态托管的视频地址") String videoUrl
) {
}
