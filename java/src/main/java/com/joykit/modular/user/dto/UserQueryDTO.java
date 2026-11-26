package com.joykit.modular.user.dto;

import com.joykit.common.result.PageParams;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户分页查询条件")
public class UserQueryDTO extends PageParams {
    @Schema(
            description = "是否统计总记录数。仅切换页码、总数已知时传 false 可省去一次统计；为 false 时响应的 total 为 null",
            defaultValue = "true")
    private Boolean withCount;
}
