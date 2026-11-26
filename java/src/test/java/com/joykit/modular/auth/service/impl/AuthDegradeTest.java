package com.joykit.modular.auth.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.joykit.modular.auth.mapper.AuthMapper;
import com.joykit.modular.auth.mapping.AuthMapping;
import com.joykit.modular.auth.service.AuthService;
import com.joykit.modular.auth.service.JwtTokens;
import com.joykit.modular.auth.service.RedisSessions;
import com.joykit.modular.user.mapper.UserMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

class AuthDegradeTest {
    private final RedisSessions sessions = mock(RedisSessions.class);
    private final AuthServiceImpl service = new AuthServiceImpl(mock(AuthMapper.class), mock(UserMapper.class),
            new AuthMapping(), new JwtTokens(Base64.getEncoder().encodeToString(new byte[32])), sessions);
    private final MutableClock clock = new MutableClock();
    private final AuthService.Identity identity = new AuthService.Identity(1, "alice", "Alice", "WEB");
    private final String token;

    AuthDegradeTest() {
        token = new JwtTokens(Base64.getEncoder().encodeToString(new byte[32]))
                .issue(identity, "access", Instant.now().plusSeconds(1800));
    }

    @BeforeEach
    void useTestClock() {
        service.setClock(clock);
    }

    @Test
    void redisFailureDegradesToTokenOnlyCheck() {
        when(sessions.active(eq(identity), anyString())).thenThrow(new RedisConnectionFailureException("down"));

        // Redis 挂了：凭证有效就放行
        assertEquals(identity, service.authenticate(token));
        assertEquals(identity, service.authenticate(token));
        // 第二次没有再访问 Redis：熔断窗口内直接放行，不会每个请求都等一次超时
        verify(sessions, times(1)).active(eq(identity), anyString());
    }

    @Test
    void sessionCheckResumesAfterDegradeWindow() {
        when(sessions.active(eq(identity), anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertEquals(identity, service.authenticate(token));

        clock.advanceSeconds(AuthServiceImpl.REDIS_DEGRADE_WINDOW_MS / 1000 + 1);
        when(sessions.active(eq(identity), anyString())).thenReturn(false);

        // Redis 恢复后重新检查会话，不能一直停在降级状态
        assertNull(service.authenticate(token));
        verify(sessions, times(2)).active(eq(identity), anyString());
    }

    @Test
    void revocationIsHonouredOnceRedisIsBack() {
        when(sessions.active(eq(identity), anyString())).thenThrow(new RedisConnectionFailureException("down"));
        assertEquals(identity, service.authenticate(token));

        clock.advanceSeconds(AuthServiceImpl.REDIS_DEGRADE_WINDOW_MS / 1000 + 1);
        when(sessions.active(eq(identity), anyString())).thenReturn(true);
        assertEquals(identity, service.authenticate(token));
        when(sessions.active(eq(identity), anyString())).thenReturn(false);
        assertNull(service.authenticate(token));
    }

    @Test
    void logoutDuringOutageIsStillRejectedWhileDegraded() {
        when(sessions.active(eq(identity), anyString())).thenThrow(new RedisConnectionFailureException("down"));
        doThrow(new RedisConnectionFailureException("down")).when(sessions).revoke(any(), anyString());

        service.logout(token);

        // 降级只对“仍在登录”的会话放行，本机已登出的会话照常拒绝
        assertNull(service.authenticate(token));
    }

    @Test
    void logoutReachesRedisWhenItIsHealthy() {
        service.logout(token);

        verify(sessions).revoke(eq(identity), anyString());
    }

    @Test
    void invalidOrMissingTokenIsRejectedWithoutTouchingRedis() {
        assertNull(service.authenticate(null));
        assertNull(service.authenticate("not-a-token"));
        assertNull(service.authenticate(token + "broken"));

        verify(sessions, never()).active(any(), anyString());
    }

    /** 可以手动拨动的时钟，用来模拟降级窗口过去 */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advanceSeconds(long seconds) { now = now.plusSeconds(seconds); }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}
