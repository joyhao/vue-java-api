package com.joykit.common.exception;

import com.joykit.common.result.Result;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<Result<Void>> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer code && code >= 400 && code <= 599 ? code : 500;
        String message = switch (status) {
            case 401 -> "请先登录";
            case 403 -> "无权访问";
            case 404 -> "接口或资源不存在";
            case 405 -> "不支持此请求方法";
            default -> "请求处理失败";
        };
        return ResponseEntity.status(status).body(
            Result.fail(status, message));
    }
}
