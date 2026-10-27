package com.joykit.api.response;

public record ApiResponse<T>(int code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(0, "操作成功", data, traceId);
    }

    public static <T> ApiResponse<T> failure(int status, String message, T data, String traceId) {
        return new ApiResponse<>(status, message, data, traceId);
    }
}
