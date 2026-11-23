package com.joykit.modular.auth;

import com.joykit.modular.auth.mapper.AuthMapper;
import com.joykit.modular.auth.mapping.AuthMapping;
import com.joykit.modular.auth.service.impl.AuthServiceImpl;
import com.joykit.modular.user.mapper.UserMapper;
import com.joykit.modular.user.po.UserPO;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshTest {
    @Test
    void refreshRotatesBothTokensWithoutExtendingAbsoluteLifetime() {
        var mapper = mock(AuthMapper.class);
        var service = new AuthServiceImpl(mapper, mock(UserMapper.class), new AuthMapping());
        Instant deadline = Instant.now().plusSeconds(600);
        when(mapper.lockRefresh(anyString())).thenReturn(new AuthMapper.RefreshSession(1, "WEB", deadline));
        UserPO user = new UserPO();
        user.setId(1L);
        user.setAccount("test");
        user.setName("Test");
        when(mapper.findEnabledUser(1)).thenReturn(user);
        when(mapper.rotate(anyString(), anyString(), anyString(), any())).thenReturn(1);
        var login = service.refresh("a".repeat(43));
        assertNotNull(login);
        assertNotEquals(login.token(), login.refreshToken());
        assertEquals(deadline, login.refreshExpiresAt());
        assertEquals(deadline, login.expiresAt());
        verify(mapper).rotate(anyString(), anyString(), anyString(), eq(deadline));
    }

    @Test
    void revokedOrExpiredRefreshDoesNotIssueNewCredentials() {
        var mapper = mock(AuthMapper.class);
        var service = new AuthServiceImpl(mapper, mock(UserMapper.class), new AuthMapping());
        assertNull(service.refresh("a".repeat(43)));
        verify(mapper, never()).rotate(anyString(), anyString(), anyString(), any());
    }
}
