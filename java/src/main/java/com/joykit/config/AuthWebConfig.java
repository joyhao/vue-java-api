package com.joykit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joykit.common.result.Result;
import com.joykit.modular.auth.controller.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthWebConfig implements WebMvcConfigurer {
    private final ObjectMapper objectMapper;

    public AuthWebConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                String method = request.getMethod();
                boolean safe = "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
                if (!safe) {
                    String fetchSite = request.getHeader("Sec-Fetch-Site");
                    if ("cross-site".equals(fetchSite) || !"joykit".equals(request.getHeader("X-Requested-With"))) {
                        response.setStatus(403);
                        response.setContentType("application/json;charset=UTF-8");
                        objectMapper.writeValue(response.getWriter(), Result.fail(403, "不允许跨站请求", null));
                        return false;
                    }
                }
                String path = request.getRequestURI().substring(request.getContextPath().length());
                if ("/api/auth/login".equals(path)) return true;
                var session = request.getSession(false);
                if (session != null && session.getAttribute(AuthController.SESSION_USER) != null) return true;
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                objectMapper.writeValue(response.getWriter(), Result.fail(401, "登录已过期，请重新登录", null));
                return false;
            }
        }).addPathPatterns("/api/**").order(-100);
    }
}
