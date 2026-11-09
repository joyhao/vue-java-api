package com.joykit.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joykit.common.annotation.DevOnlyController;
import com.joykit.common.annotation.DevOnlyMethod;
import com.joykit.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 拦截带有 {@link DevOnlyController} 或 {@link DevOnlyMethod} 的接口，
 * 在非开发环境下拒绝访问。
 */
@Slf4j
@Component
public class DevAccessInterceptor implements HandlerInterceptor {

    private final Environment environment;
    private final ObjectMapper objectMapper;
    private final Set<String> devProfiles;
    private final boolean allowDefaultProfile;

    public DevAccessInterceptor(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
        this.devProfiles = Arrays.stream(
                        environment.getProperty("app.dev-access.profiles", "dev,local").split(","))
                .map(String::trim)
                .filter(profile -> !profile.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.allowDefaultProfile = environment.getProperty(
                "app.dev-access.allow-default-profile", Boolean.class, Boolean.TRUE);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod) || isDevelopmentEnvironment()) {
            return true;
        }

        Class<?> controllerType = ClassUtils.getUserClass(handlerMethod.getBeanType());
        boolean restricted = AnnotatedElementUtils.hasAnnotation(controllerType, DevOnlyController.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), DevOnlyMethod.class);
        if (!restricted) {
            return true;
        }

        log.warn("拒绝非开发环境访问受限接口: {} {}", request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(
                response.getOutputStream(),
                Result.fail(HttpServletResponse.SC_FORBIDDEN, "该接口仅限开发环境访问"));
        return false;
    }

    private boolean isDevelopmentEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return allowDefaultProfile;
        }
        return Arrays.stream(activeProfiles).anyMatch(devProfiles::contains);
    }
}
