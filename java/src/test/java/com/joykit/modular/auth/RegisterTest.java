package com.joykit.modular.auth;

import com.joykit.common.exception.GlobalExceptionHandler;
import com.joykit.modular.auth.controller.AuthController;
import com.joykit.modular.auth.service.AuthService;
import com.joykit.modular.auth.service.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegisterTest {
    private AuthService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(AuthService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void registrationSucceeds() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"test_user\",\"password\":\"TestPassword123\",\"confirmPassword\":\"TestPassword123\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data").value(true));
        verify(service).register("test_user", "TestPassword123");
    }

    @Test
    void duplicateAccountReturnsConflict() throws Exception {
        doThrow(new DuplicateKeyException("duplicate key violates user_account_unique")).when(service).register(anyString(), anyString());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"test_user\",\"password\":\"TestPassword123\",\"confirmPassword\":\"TestPassword123\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("账号已存在"));
    }

    @Test
    void mismatchedPasswordsNeverReachService() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"test_user\",\"password\":\"TestPassword123\",\"confirmPassword\":\"different\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void invalidAccountAndShortPasswordAreRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"1\",\"password\":\"short\",\"confirmPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void serviceStoresHashNotPlaintext() {
        var userMapper = mock(com.joykit.modular.user.mapper.UserMapper.class);
        var impl = new com.joykit.modular.auth.service.impl.AuthServiceImpl(
                mock(com.joykit.modular.auth.mapper.AuthMapper.class), userMapper,
                new com.joykit.modular.auth.mapping.AuthMapping(), mock(com.joykit.modular.auth.service.JwtTokens.class),
                mock(com.joykit.modular.auth.service.RedisSessions.class));
        impl.register("test_user", "TestPassword123");
        var user = ArgumentCaptor.forClass(com.joykit.modular.user.po.UserPO.class);
        verify(userMapper).insert(user.capture());
        assertEquals("test_user", user.getValue().getAccount());
        assertNotEquals("TestPassword123", user.getValue().getPasswordHash());
        assertTrue(PasswordHasher.matches("TestPassword123", user.getValue().getPasswordHash()));
    }
}
