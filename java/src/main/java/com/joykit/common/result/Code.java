package com.joykit.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Code {
    success(200, "操作成功"),
    failure(500, "业务异常"),
    unauthorized(401, "未授权"),
    FORBIDDEN(403, "拒绝访问"),
    not_found(404, "资源不存在"),
    param_error(400, "参数错误");

    private final Integer code;
    private final String message;
}
