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
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(1800);
        authMapper.saveSession(user.getId(), clientType.name(), digest(token), now, expires);
        String refreshToken = randomToken();
        Instant refreshExpires = now.plusSeconds(604800);
        authMapper.setRefresh(digest(token), digest(refreshToken), refreshExpires);
        authMapper.updateLastLogin(user.getId(), now);
        return new Login(authMapping.poToIdentity(user, clientType.name()), token, expires, refreshToken, refreshExpires);
    }

    @Override
    @Transactional(readOnly = true)
    public Identity authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        String hash = digest(token);
        UserPO user = authMapper.findByTokenHash(hash);
        if (user == null) return null;
        String clientType = authMapper.findClientType(hash);
        return clientType == null ? null : authMapping.poToIdentity(user, clientType);
    }

    @Override
    @Transactional
    public void logout(String token) {
        if (token != null) authMapper.deleteSession(digest(token));
    }

    @Override
    @Transactional
    public Login refresh(String refreshToken) {
        if (refreshToken == null || !refreshToken.matches("[A-Za-z0-9_-]{43}")) return null;
        String previous = digest(refreshToken);
        var session = authMapper.lockRefresh(previous);
        if (session == null) return null;
        UserPO user = authMapper.findEnabledUser(session.userId());
        if (user == null) return null;
        String access = randomToken();
        String next = randomToken();
        Instant expires = Instant.now().plusSeconds(1800);
        if (expires.isAfter(session.expires())) expires = session.expires();
        if (authMapper.rotate(previous, digest(access), digest(next), expires) != 1) return null;
        return new Login(authMapping.poToIdentity(user, session.clientType()), access, expires, next, session.expires());
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
