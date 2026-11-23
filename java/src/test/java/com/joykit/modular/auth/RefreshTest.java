package com.joykit.modular.auth;

import com.joykit.modular.auth.mapper.AuthMapper;
import com.joykit.modular.auth.mapping.AuthMapping;
import com.joykit.modular.auth.service.AuthService;
import com.joykit.modular.auth.service.JwtTokens;
import com.joykit.modular.auth.service.impl.AuthServiceImpl;
import com.joykit.modular.user.mapper.UserMapper;
import com.joykit.modular.user.po.UserPO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTest {
    private JwtTokens codec() {
        return new JwtTokens(Base64.getEncoder().encodeToString(new byte[32]));
    }
    private final AuthService.Identity identity = new AuthService.Identity(1, "test", "Test", "WEB");

    @Test
    void refreshRetainsAbsoluteDeadline() {
        var mapper = mock(AuthMapper.class);
        var jwt = codec();
        var sessions = mock(com.joykit.modular.auth.service.RedisSessions.class);
        var service = new AuthServiceImpl(mapper, mock(UserMapper.class), new AuthMapping(), jwt, sessions);
        Instant deadline = Instant.now().plusSeconds(600).truncatedTo(ChronoUnit.SECONDS);
        UserPO user = new UserPO();
        user.setId(1L); user.setAccount("test"); user.setName("Test");
        when(mapper.findEnabledUser(1)).thenReturn(user);
        String token = jwt.issue(identity, "refresh", deadline);
        when(sessions.rotate(any(), anyString(), eq(token), anyString())).thenReturn(true, false);
        var result = service.refresh(token);
        assertNotNull(result);
        assertEquals(deadline, result.refreshExpiresAt());
        assertEquals(deadline, result.expiresAt());
        assertNotNull(jwt.verify(result.token(), "access"));
        assertNull(service.refresh(token));
    }

    @Test
    void rejectsExpiredTamperedAndWrongPurposeTokens() {
        var jwt = codec();
        String access = jwt.issue(identity, "access", Instant.now().plusSeconds(600));
        assertNotNull(jwt.verify(access, "access"));
        assertNull(jwt.verify(access, "refresh"));
        assertNull(jwt.verify(access.substring(0, access.lastIndexOf('.') + 1) + "invalid", "access"));
        assertNull(jwt.verify(jwt.issue(identity, "refresh", Instant.now().minusSeconds(1)), "refresh"));
        byte[] other = new byte[32]; other[0] = 1;
        assertNull(new JwtTokens(Base64.getEncoder().encodeToString(other)).verify(access, "access"));
    }

    @Test
    void accessVerificationDoesNotReadDatabase() {
        var mapper = mock(AuthMapper.class);
        var jwt = codec();
        var sessions = mock(com.joykit.modular.auth.service.RedisSessions.class);
        var service = new AuthServiceImpl(mapper, mock(UserMapper.class), new AuthMapping(), jwt, sessions);
        String access = jwt.issue(identity, "access", Instant.now().plusSeconds(600));
        when(sessions.active(eq(identity), anyString())).thenReturn(true, false);
        assertEquals(identity, service.authenticate(access));
        service.logout(access);
        verify(sessions).revoke(eq(identity), anyString());
        assertNull(service.authenticate(access));
        verifyNoInteractions(mapper);
    }
}
