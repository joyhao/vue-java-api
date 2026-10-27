package com.joykit.common.exception;

import com.joykit.common.result.Result;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
            errors.computeIfAbsent(error.getField(), key -> new java.util.ArrayList<>())
                .add(error.getDefaultMessage() == null ? "参数无效" : error.getDefaultMessage()));
        return response(HttpStatus.BAD_REQUEST, "请求参数校验失败", Map.of("errors", errors), headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            LOG.error("MVC 请求处理失败", exception);
        }
        return response(status, status.is5xxServerError() ? "服务暂时不可用" : "请求无效或资源不存在",
            null, headers, request);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusiness(BusinessException exception, WebRequest request) {
        HttpStatus status = switch (exception.kind()) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
        };
        return response(status, exception.getMessage(), null, new HttpHeaders(), request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Object> handleDatabase(DataAccessException exception, WebRequest request) {
        LOG.error("数据库操作失败", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "数据操作失败，请稍后重试", null, new HttpHeaders(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        LOG.error("请求处理发生未预期异常", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "服务暂时不可用，请稍后重试", null, new HttpHeaders(), request);
    }

    private ResponseEntity<Object> response(HttpStatusCode status, String message, Object data,
            HttpHeaders headers, WebRequest request) {
        return new ResponseEntity<>(Result.fail(status.value(), message, data), headers, status);
    }
}
