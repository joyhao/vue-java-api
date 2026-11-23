package com.joykit.modular.auth.service;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;

public interface AuthService {
    enum ClientType { WEB, APP }
    record Identity(@JsonSerialize(using = ToStringSerializer.class) long id, String account, String name, String clientType) {}
    record Login(Identity user, String token, Instant expiresAt, String refreshToken, Instant refreshExpiresAt) {
        public Login(Identity user, String token, Instant expiresAt) { this(user, token, expiresAt, null, null); }
    }
    Login refresh(String refreshToken);

    void register(String account, String password);
    Login login(String account, String password, ClientType clientType);
    Identity authenticate(String token);
    void logout(String token);
}
