package com.joykit.modular.video.controller;

import com.joykit.common.annotation.DevOnlyController;
import com.joykit.common.constants.ApiConstants;
import com.joykit.common.result.PageParams;
import com.joykit.common.result.PageResult;
import com.joykit.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "视频管理", description = "视频列表接口，当前使用内存模拟数据")
@RestController
@RequestMapping(ApiConstants.API_PREFIX + "/videos")
@DevOnlyController
public class VideoController {


    @Operation(summary = "分页查询视频列表", description = "基于内存模拟数据分页，不读取数据库")
    @PostMapping("/page")
    public Result<PageResult<Object>> list(@Valid @RequestBody PageParams params) {

        return null;
    }

}
