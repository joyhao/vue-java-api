package com.joykit.common.result;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageParams implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Min(1)
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    private Integer pageIndex = 1;

    @NotNull
    @Min(1)
    @Max(1000)
    @Schema(description = "每页数量", defaultValue = "10")
    private Integer pageSize = 10;

    @Size(max = 100)
    @Schema(description = "关键字")
    private String keyword;
}
