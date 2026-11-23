package com.joykit.modular.auth.service.impl;

import com.joykit.modular.auth.mapper.AuthMapper;
import com.joykit.modular.auth.mapping.AuthMapping;
import com.joykit.modular.auth.service.AuthService;
import com.joykit.modular.auth.service.PasswordHasher;
import com.joykit.modular.user.mapper.UserMapper;
import com.joykit.modular.user.po.UserPO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AuthMapper authMapper;
    private final UserMapper userMapper;
    private final AuthMapping authMapping;
    private final com.joykit.modular.auth.service.JwtTokens jwtTokens;
    private final com.joykit.modular.auth.service.RedisSessions sessions;
    private final String dummyHash = PasswordHasher.hash("not-a-real-user-password");

    @Override
    public void register(String account, String password) {
        long started = System.nanoTime();
        String passwordHash = PasswordHasher.hash(password);
        long hashed = System.nanoTime();
        UserPO user = new UserPO();
        user.setAccount(account);
        user.setName(account);
        user.setPasswordHash(passwordHash);
        user.setStatus(1);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(user.getCreatedAt());
        userMapper.insert(user);
        long finished = System.nanoTime();
        org.slf4j.LoggerFactory.getLogger(AuthServiceImpl.class).info(
                "注册耗时: 密码哈希={}ms, 数据库写入={}ms, 总计={}ms",
                (hashed - started) / 1_000_000, (finished - hashed) / 1_000_000,
                (finished - started) / 1_000_000);
    }

    @Override
    @Transactional
    public Login login(String account, String password, ClientType clientType) {
        UserPO user = authMapper.findByAccount(account.trim());
        boolean valid = PasswordHasher.matches(password,
                user == null || user.getPasswordHash() == null ? dummyHash : user.getPasswordHash());
        if (user == null || !valid || !Integer.valueOf(1).equals(user.getStatus())) return null;
        Instant now = Instant.now();
        authMapper.updateLastLogin(user.getId(), now);
        String sid = java.util.UUID.randomUUID().toString();
        var login = issue(authMapping.poToIdentity(user, clientType.name()), now.plusSeconds(604800), sid);
        sessions.start(login.user(), sid, login.refreshToken(), login.refreshExpiresAt());
        return login;
    }

    @Override
    public Identity authenticate(String token) {
        var claims = jwtTokens.verify(token, "access");
        return claims != null && sessions.active(claims.user(), claims.sessionId()) ? claims.user() : null;
    }

    @Override
    public void logout(String token) {
        var claims = jwtTokens.verify(token, "access");
        if (claims != null) sessions.revoke(claims.user(), claims.sessionId());
    }

    @Override
    public Login refresh(String refreshToken) {
        var claims = jwtTokens.verify(refreshToken, "refresh");
        if (claims == null) return null;
        UserPO user = authMapper.findEnabledUser(claims.user().id());
        if (user == null) return null;
        var login = issue(authMapping.poToIdentity(user, claims.user().clientType()), claims.expiresAt(), claims.sessionId());
        return sessions.rotate(login.user(), claims.sessionId(), refreshToken, login.refreshToken()) ? login : null;
    }

    private Login issue(Identity user, Instant refreshExpires, String sid) {
        Instant expires = Instant.now().plusSeconds(1800);
        if (expires.isAfter(refreshExpires)) expires = refreshExpires;
        return new Login(user, jwtTokens.issue(user, "access", expires, sid), expires,
                jwtTokens.issue(user, "refresh", refreshExpires, sid), refreshExpires);
    }
}
