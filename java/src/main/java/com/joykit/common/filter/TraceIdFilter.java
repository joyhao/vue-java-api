package com.joykit.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceIdFilter extends OncePerRequestFilter {
    public static final String ATTRIBUTE = "traceId";

    public static String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        if (value == null) {
            value = UUID.randomUUID().toString();
            request.setAttribute(ATTRIBUTE, value);
        }
        return value.toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                   FilterChain chain) throws ServletException, IOException {
        String traceId = traceId(request);
        response.setHeader("X-Trace-Id", traceId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(ATTRIBUTE, traceId)) {
            chain.doFilter(request, response);
        }
    }
}
