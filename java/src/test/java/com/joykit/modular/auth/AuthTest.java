package com.joykit.modular.auth;

import com.joykit.modular.auth.controller.AuthController;
import com.joykit.modular.auth.service.AuthService;
import com.joykit.modular.auth.service.PasswordHasher;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthTest {
    @Test
    void passwordsAreSaltedAndVerified() {
        String hash = PasswordHasher.hash("test-password-only");
        assertTrue(PasswordHasher.matches("test-password-only", hash));
        assertFalse(PasswordHasher.matches("wrong", hash));
        assertFalse(PasswordHasher.matches("test-password-only", "plaintext"));
        assertNotEquals(hash, PasswordHasher.hash("test-password-only"));
    }

    @Test
    void webSendsRefreshTokenOnlyAsHttpOnlyCookie() {
        var service = mock(AuthService.class);
        var user = new AuthService.Identity(1, "test", "Test", "WEB");
        when(service.login("test", "password", AuthService.ClientType.WEB))
                .thenReturn(new AuthService.Login(user, "test-token", Instant.now().plusSeconds(1800),
                        "test-refresh-token", Instant.now().plusSeconds(86400)));
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var result = new AuthController(service, mock(com.joykit.modular.auth.service.LoginRateLimiter.class)).login(new com.joykit.modular.auth.dto.LoginDTO("test", "password", null), new MockHttpServletRequest(), response);
        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.contains("joykit_refresh=test-refresh-token"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Max-Age="));
        assertFalse(cookie.contains("test-token"));
        assertEquals("test-token", result.getData().token());
        assertNull(result.getData().refreshToken());
    }

    @Test
    void appDoesNotOverwriteBrowserCookie() {
        var service = mock(AuthService.class);
        var user = new AuthService.Identity(1, "test", "Test", "APP");
        when(service.login("test", "password", AuthService.ClientType.APP))
                .thenReturn(new AuthService.Login(user, "app-token", Instant.now().plusSeconds(1800)));
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var result = new AuthController(service, mock(com.joykit.modular.auth.service.LoginRateLimiter.class)).login(new com.joykit.modular.auth.dto.LoginDTO("test", "password", AuthService.ClientType.APP), new MockHttpServletRequest(), response);
        assertNull(response.getHeader("Set-Cookie"));
        assertEquals("app-token", result.getData().token());
        assertEquals(200, response.getStatus());
    }

    @Test
    void logoutClearsBrowserRefreshCookie() {
        var service = mock(AuthService.class);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer test-token");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        new AuthController(service, mock(com.joykit.modular.auth.service.LoginRateLimiter.class)).logout(request, response);
        String cookie = response.getHeader("Set-Cookie");
        assertTrue(cookie.contains("joykit_refresh="));
        assertTrue(cookie.contains("Max-Age=0"));
        verify(service).logout("test-token");
    }

    @Test
    void invalidCredentialsReturnUnauthorized() {
        var service = mock(AuthService.class);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        new AuthController(service, mock(com.joykit.modular.auth.service.LoginRateLimiter.class)).login(new com.joykit.modular.auth.dto.LoginDTO("missing", "bad", null), new MockHttpServletRequest(), response);
        assertEquals(401, response.getStatus());
    }
}
