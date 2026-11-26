package com.joykit.modular.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.joykit.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class LoginRateLimiterTest {
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final LoginRateLimiter limiter = new LoginRateLimiter(redis);

    private static final String FAIL_KEY = "joykit:auth:rl:fail:alice";
    private static final String LOGIN_IP_KEY = "joykit:auth:rl:login-ip:10.0.0.1";

    @BeforeEach
    void wireRedis() {
        when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    void accountIsLockedAfterThresholdFailures() {
        when(ops.get(FAIL_KEY)).thenReturn(String.valueOf(LoginRateLimiter.FAIL_LOCK_THRESHOLD));

        var failure = assertThrows(BusinessException.class,
                () -> limiter.checkLoginAllowed("10.0.0.1", "Alice"));

        assertEquals(BusinessException.Kind.RATE_LIMITED, failure.kind());
        // 已锁定的账号不需要再数 IP 尝试
        verify(ops, org.mockito.Mockito.never()).increment(anyString());
    }

    @Test
    void ipWindowBlocksExcessAttempts() {
        when(ops.get(FAIL_KEY)).thenReturn(null);
        when(ops.increment(LOGIN_IP_KEY)).thenReturn((long) LoginRateLimiter.LOGIN_IP_LIMIT + 1);

        var failure = assertThrows(BusinessException.class,
                () -> limiter.checkLoginAllowed("10.0.0.1", "alice"));

        assertEquals(BusinessException.Kind.RATE_LIMITED, failure.kind());
    }

    @Test
    void underLimitsIsAllowedAndFirstAttemptSetsExpiry() {
        when(ops.get(FAIL_KEY)).thenReturn("4");
        when(ops.increment(LOGIN_IP_KEY)).thenReturn(1L);

        assertDoesNotThrow(() -> limiter.checkLoginAllowed("10.0.0.1", "alice"));

        verify(redis).expire(LOGIN_IP_KEY, LoginRateLimiter.LOGIN_IP_WINDOW);
    }

    @Test
    void redisDowntimeFailsOpenInsteadOfBlockingLogin() {
        when(ops.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertDoesNotThrow(() -> limiter.checkLoginAllowed("10.0.0.1", "alice"));
        assertDoesNotThrow(() -> limiter.recordLoginFailure("10.0.0.1", "alice"));
        assertDoesNotThrow(() -> limiter.recordLoginSuccess("alice"));
        assertDoesNotThrow(() -> limiter.checkRegisterAllowed("10.0.0.1"));
    }

    @Test
    void failureCountingAndSuccessReset() {
        when(ops.increment(FAIL_KEY)).thenReturn(1L);

        limiter.recordLoginFailure("10.0.0.1", "Alice");
        verify(redis).expire(FAIL_KEY, LoginRateLimiter.FAIL_LOCK_WINDOW);

        limiter.recordLoginSuccess("ALICE");
        verify(redis).delete(FAIL_KEY);
    }

    @Test
    void registerIsBlockedAfterIpLimit() {
        when(ops.increment("joykit:auth:rl:register-ip:10.0.0.1"))
                .thenReturn((long) LoginRateLimiter.REGISTER_IP_LIMIT + 1);

        var failure = assertThrows(BusinessException.class, () -> limiter.checkRegisterAllowed("10.0.0.1"));

        assertEquals(BusinessException.Kind.RATE_LIMITED, failure.kind());
    }

    @Test
    void garbageCounterValueDoesNotExplode() {
        when(ops.get(FAIL_KEY)).thenReturn("not-a-number");
        when(ops.increment(LOGIN_IP_KEY)).thenReturn(1L);

        assertDoesNotThrow(() -> limiter.checkLoginAllowed("10.0.0.1", "alice"));
    }
}
