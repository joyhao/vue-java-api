package com.joykit.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;

@Getter
@Schema(description = "结果分页")
public class PageResult<T> {
    @Schema(description = "总记录数")
    private final long total;
    @Schema(description = "数据列表")
    private final List<T> list;

    public PageResult(long total, List<T> list) {
        this.total = total;
        this.list = List.copyOf(list);
    }

    public static <T> PageResult<T> of(long total, List<T> list) {
        return new PageResult<>(total, list);
    }
}
