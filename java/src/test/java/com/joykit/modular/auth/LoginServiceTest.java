package com.joykit.modular.auth;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.joykit.modular.auth.mapper.AuthMapper;
import com.joykit.modular.auth.mapping.AuthMapping;
import com.joykit.modular.auth.service.AuthService;
import com.joykit.modular.auth.service.JwtTokens;
import com.joykit.modular.auth.service.PasswordHasher;
import com.joykit.modular.auth.service.RedisSessions;
import com.joykit.modular.auth.service.impl.AuthServiceImpl;
import com.joykit.modular.user.mapper.UserMapper;
import com.joykit.modular.user.po.UserPO;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

class LoginServiceTest {
    private final AuthMapper mapper = mock(AuthMapper.class);
    private final RedisSessions sessions = mock(RedisSessions.class);
    private final AuthServiceImpl service = new AuthServiceImpl(mapper, mock(UserMapper.class), new AuthMapping(),
            new JwtTokens(Base64.getEncoder().encodeToString(new byte[32])), sessions);
    private final UserPO user = new UserPO();

    @BeforeEach
    void existingUser() {
        user.setId(7L);
        user.setAccount("alice");
        user.setName("Alice");
        user.setStatus(1);
        user.setPasswordHash(PasswordHasher.hash("correct-password"));
        when(mapper.findByAccount("alice")).thenReturn(user);
        when(mapper.updateLastLogin(eq(7L), any())).thenReturn(1);
    }

    @Test
    void correctPasswordStartsSessionThenUpdatesLastLogin() {
        AuthService.Login login = service.login("alice", "correct-password", AuthService.ClientType.WEB);

        assertNotNull(login);
        // 先写 Redis 再写数据库：Redis 失败时数据库不会被改动
        InOrder order = inOrder(mapper, sessions);
        order.verify(mapper).findByAccount("alice");
        order.verify(sessions).start(any(), anyString(), eq(login.refreshToken()), any());
        order.verify(mapper).updateLastLogin(eq(7L), any(Instant.class));
    }

    @Test
    void redisFailureLeavesDatabaseUntouched() {
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"))
                .when(sessions).start(any(), anyString(), anyString(), any());

        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> service.login("alice", "correct-password", AuthService.ClientType.WEB));

        verify(mapper, never()).updateLastLogin(anyLong(), any());
    }

    @Test
    void wrongPasswordWritesNothing() {
        assertNull(service.login("alice", "wrong-password", AuthService.ClientType.WEB));

        verify(mapper, never()).updateLastLogin(anyLong(), any());
        verifyNoInteractions(sessions);
    }

    @Test
    void unknownAccountWritesNothing() {
        assertNull(service.login("nobody", "whatever-password", AuthService.ClientType.WEB));

        verify(mapper, never()).updateLastLogin(anyLong(), any());
        verifyNoInteractions(sessions);
    }

    @Test
    void disabledAccountWritesNothing() {
        user.setStatus(0);

        assertNull(service.login("alice", "correct-password", AuthService.ClientType.WEB));

        verify(mapper, never()).updateLastLogin(anyLong(), any());
        verifyNoInteractions(sessions);
    }

    @Test
    void userDeletedDuringPasswordCheckGetsNoTokens() {
        when(mapper.updateLastLogin(eq(7L), any())).thenReturn(0);

        assertNull(service.login("alice", "correct-password", AuthService.ClientType.WEB));

        // 刚建好的会话要收回，不能留下孤儿会话
        verify(sessions).revoke(any(), anyString());
    }

    @Test
    void loginMustNotHoldConnectionDuringPasswordCheck() throws Exception {
        // 加上 @Transactional 会把查用户和更新登录时间包回一个事务，密码校验期间一直占着连接。
        assertNull(AuthServiceImpl.class
                .getMethod("login", String.class, String.class, AuthService.ClientType.class)
                .getAnnotation(Transactional.class));
    }
}
