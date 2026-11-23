package com.joykit.modular.video.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "视频同步结果")
public class VideoSyncVO {
    @Schema(description = "扫描到的视频文件数")
    private long scanned;
    @Schema(description = "新增记录数")
    private long inserted;
    @Schema(description = "更新记录数")
    private long updated;
    @Schema(description = "清理的失效记录数")
    private long removed;
    @Schema(description = "同步后库内总记录数")
    private long total;
}
