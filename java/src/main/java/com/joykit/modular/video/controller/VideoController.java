package com.joykit.modular.video.controller;

import com.joykit.common.annotation.DevOnlyController;
import com.joykit.common.constants.ApiConstants;
import com.joykit.common.result.PageParams;
import com.joykit.common.result.PageResult;
import com.joykit.common.result.Result;
import com.joykit.modular.video.service.VideoService;
import com.joykit.modular.video.service.VideoService2;
import com.joykit.modular.video.vo.VideoSyncVO;
import com.joykit.modular.video.vo.VideoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "视频管理", description = "")
@RestController
@RequestMapping(ApiConstants.API_PREFIX + "/videos")
@DevOnlyController
public class VideoController {

    private final VideoService videoService;
    private final VideoService2 videoService2;

    public VideoController(VideoService videoService,VideoService2 videoService2) {
        this.videoService = videoService;
        this.videoService2 = videoService2;
    }

    @Operation(summary = "分页查询视频列表", description = "递归扫描配置的视频目录，返回元数据、缩略图和视频 HTTP 地址")
    @PostMapping("/page")
    public Result<PageResult<VideoVO>> list(@Valid @RequestBody PageParams params) {
        return videoService.findPage(params);
    }

    @Operation(summary = "同步视频数据", description = "扫描视频目录，把新增/变更的元数据写入数据库并清理已删除的记录")
    @PostMapping("/sync")
    public Result<VideoSyncVO> sync() {
        return videoService.sync();
    }

    @Operation(summary = "探测视频时长", description = "首次调用使用 FFprobe 读取时长并写入数据库")
    @GetMapping("/{id}/duration")
    public Result<Double> duration(@PathVariable String id) {
        return Result.success(videoService2.resolveDuration(id));
    }


    @Operation(summary = "获取视频缩略图", description = "按需生成并返回视频画面")
    @GetMapping(value = "/{id}/thumbnail", produces = MediaType.IMAGE_JPEG_VALUE)
    public void thumbnail(@PathVariable String id, HttpServletResponse response) throws IOException {
        Path path = videoService.resolveThumbnail(id);
        response.setContentType(MediaType.IMAGE_JPEG_VALUE);
        response.setContentLengthLong(Files.size(path));
        try (InputStream source = Files.newInputStream(path);
             OutputStream output = response.getOutputStream()) {
            source.transferTo(output);
            output.flush();
        } catch (IOException ignored) {
            // 客户端提前关闭图片请求
        }
    }
}
