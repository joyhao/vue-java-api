package com.joykit.config;

import com.joykit.common.interceptor.DevAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class DevAccessWebConfig implements WebMvcConfigurer {

    private final DevAccessInterceptor devAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(devAccessInterceptor).addPathPatterns("/**");
    }
}
