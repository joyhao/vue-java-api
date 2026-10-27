package com.joykit.api.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joykit.api.filter.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestControllerAdvice
public class UnifiedResponseAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper mapper;
    private final HttpServletRequest servletRequest;

    public UnifiedResponseAdvice(ObjectMapper mapper, HttpServletRequest servletRequest) {
        this.mapper = mapper;
        this.servletRequest = servletRequest;
    }

    @Override
    public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) {
        return !Resource.class.isAssignableFrom(type.getParameterType())
            && !StreamingResponseBody.class.isAssignableFrom(type.getParameterType())
            && !SseEmitter.class.isAssignableFrom(type.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter type, MediaType mediaType,
                                  Class<? extends HttpMessageConverter<?>> converter,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?> || body instanceof Resource || body instanceof byte[]
            || !MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
                && !MediaType.TEXT_PLAIN.isCompatibleWith(mediaType)) {
            return body;
        }
        ApiResponse<Object> result = ApiResponse.success(body, TraceIdFilter.traceId(servletRequest));
        if (org.springframework.http.converter.StringHttpMessageConverter.class.isAssignableFrom(converter)) {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            try {
                return mapper.writeValueAsString(result);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("响应序列化失败", exception);
            }
        }
        return result;
    }
}
