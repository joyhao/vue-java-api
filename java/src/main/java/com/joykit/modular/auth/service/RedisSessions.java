package com.joykit.modular.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisSessions {
    /** 刷新结果：ROTATED 已轮换；NOT_FOUND 会话不存在、已失效或不属于该凭证；REPLAYED 旧凭证被重放，会话已吊销 */
    public enum Rotation { ROTATED, NOT_FOUND, REPLAYED }

    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> ROTATE = loadScript("lua/auth/rotate-session.lua");
    private static final DefaultRedisScript<Long> DELETE = loadScript("lua/auth/revoke-session.lua");

    private static DefaultRedisScript<Long> loadScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new org.springframework.core.io.ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    public void start(AuthService.Identity user, String sid, String refresh, Instant expires) {
        redis.opsForValue().set(key(user), sid + ":" + hash(refresh), Duration.between(Instant.now(), expires));
    }

    public boolean active(AuthService.Identity user, String sid) {
        String value = redis.opsForValue().get(key(user));
        return value != null && value.startsWith(sid + ":");
    }

    public Rotation rotate(AuthService.Identity user, String sid, String oldRefresh, String nextRefresh) {
        Long result = redis.execute(ROTATE, List.of(key(user)),
                sid + ":" + hash(oldRefresh), sid + ":" + hash(nextRefresh), sid);
        if (Long.valueOf(1).equals(result)) return Rotation.ROTATED;
        return Long.valueOf(-1).equals(result) ? Rotation.REPLAYED : Rotation.NOT_FOUND;
    }

    public void revoke(AuthService.Identity user, String sid) {
        redis.execute(DELETE, List.of(key(user)), sid);
    }

    private String key(AuthService.Identity user) { return "joykit:auth:session:" + user.id() + ":" + user.clientType(); }

    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
}
