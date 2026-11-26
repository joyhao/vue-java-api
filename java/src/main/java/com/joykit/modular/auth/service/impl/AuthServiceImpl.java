package com.joykit.modular.auth.service.impl;

import com.joykit.modular.auth.mapper.AuthMapper;
import com.joykit.modular.auth.mapping.AuthMapping;
import com.joykit.modular.auth.service.AuthService;
import com.joykit.modular.auth.service.PasswordHasher;
import com.joykit.modular.auth.service.RedisSessions;
import com.joykit.modular.auth.service.RedisSessions.Rotation;
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

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final AuthMapper authMapper;
    private final UserMapper userMapper;
    private final AuthMapping authMapping;
    private final com.joykit.modular.auth.service.JwtTokens jwtTokens;
    private final com.joykit.modular.auth.service.RedisSessions sessions;
    private final String dummyHash = PasswordHasher.hash("not-a-real-user-password");
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AuthServiceImpl.class);
    /** Redis 不可用后的降级窗口：这段时间内不再访问 Redis，只校验访问凭证 */
    static final long REDIS_DEGRADE_WINDOW_MS = 10_000;
    /** 本机登出名单超过这个条数时清理一次 */
    private static final int LOCAL_REVOKE_LIMIT = 1000;
    private volatile long redisDownUntil;
    /** Redis 不可用期间在本机登出的会话（sid -> 凭证过期时间），降级放行时仍然拒绝 */
    private final java.util.concurrent.ConcurrentHashMap<String, Instant> revokedLocally =
            new java.util.concurrent.ConcurrentHashMap<>();
    private java.time.Clock clock = java.time.Clock.systemUTC();

    /** 供测试替换时钟 */
    void setClock(java.time.Clock clock) { this.clock = clock; }

    @Override
    public void register(String account, String password) {
        long started = System.nanoTime();
        // 表按 id 哈希分区后无法保留跨分区的账号唯一索引（唯一索引必须包含分区键），
        // 查重移到应用层；并发窗口极小，库内仍保留分区级校验兜底
        if (authMapper.findByAccount(account.trim()) != null) {
            throw new com.joykit.common.exception.BusinessException(
                    com.joykit.common.exception.BusinessException.Kind.CONFLICT, "账号已存在");
        }
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
    public Login login(String account, String password, ClientType clientType) {
        // 不要给这个方法加 @Transactional：查用户和更新登录时间各自自动提交，
        // 数据库连接只在执行这两条语句的瞬间占用，耗时的密码校验期间不占用连接。
        // 被一个大事务包住时，并发登录会把连接池占满。
        long started = System.nanoTime();
        UserPO user = authMapper.findByAccount(account.trim());
        long fetched = System.nanoTime();
        boolean valid = PasswordHasher.matches(password,
                user == null || user.getPasswordHash() == null ? dummyHash : user.getPasswordHash());
        long hashed = System.nanoTime();
        if (user == null || !valid || !Integer.valueOf(1).equals(user.getStatus())) return null;
        Instant now = Instant.now();
        String sid = java.util.UUID.randomUUID().toString();
        var login = issue(authMapping.poToIdentity(user, clientType.name()), now.plusSeconds(604800), sid);
        // 先写 Redis 再写数据库：Redis 不可用时这里抛异常，数据库不会被改动
        sessions.start(login.user(), sid, login.refreshToken(), login.refreshExpiresAt());
        long stored = System.nanoTime();
        // 校验期间用户可能已被删除：更新不到行就吊销刚建的会话，不发凭证
        if (authMapper.updateLastLogin(user.getId(), now) == 0) {
            sessions.revoke(login.user(), sid);
            return null;
        }
        long finished = System.nanoTime();
        LOG.info("登录耗时: 查询={}ms, 密码哈希={}ms, Redis与签发={}ms, 更新={}ms, 总计={}ms",
                (fetched - started) / 1_000_000, (hashed - fetched) / 1_000_000,
                (stored - hashed) / 1_000_000, (finished - stored) / 1_000_000,
                (finished - started) / 1_000_000);
        return login;
    }

    @Override
    public Identity authenticate(String token) {
        var claims = jwtTokens.verify(token, "access");
        if (claims == null) return null;
        // 名单里的条目在凭证过期前一直有效；凭证过期后走不到这里
        if (revokedLocally.containsKey(claims.sessionId())) return null;
        if (clock.millis() < redisDownUntil) return claims.user();
        try {
            return sessions.active(claims.user(), claims.sessionId()) ? claims.user() : null;
        } catch (org.springframework.dao.DataAccessException exception) {
            // 降级：Redis 不可用期间只校验访问凭证（最长 30 分钟过期），不再检查会话。
            // 代价是这段时间内被吊销的会话仍会被接受。
            redisDownUntil = clock.millis() + REDIS_DEGRADE_WINDOW_MS;
            LOG.warn("Redis 不可用，接下来 {} 秒只校验访问凭证、不检查会话", REDIS_DEGRADE_WINDOW_MS / 1000, exception);
            return claims.user();
        }
    }

    @Override
    public void logout(String token) {
        var claims = jwtTokens.verify(token, "access");
        if (claims == null) return;
        try {
            sessions.revoke(claims.user(), claims.sessionId());
        } catch (org.springframework.dao.DataAccessException exception) {
            // Redis 不可用时登出只在本机生效，避免降级期间已登出的凭证仍被接受
            LOG.warn("Redis 不可用，登出仅在本机生效", exception);
            revokedLocally.put(claims.sessionId(), claims.expiresAt());
            // 条目要等凭证过期才失效，长时间不可用时清理一下已过期的
            if (revokedLocally.size() > LOCAL_REVOKE_LIMIT) {
                revokedLocally.values().removeIf(deadline -> deadline.isBefore(clock.instant()));
            }
        }
    }

    @Override
    public Login refresh(String refreshToken) {
        var claims = jwtTokens.verify(refreshToken, "refresh");
        if (claims == null) return null;
        UserPO user = authMapper.findEnabledUser(claims.user().id());
        if (user == null) return null;
        var login = issue(authMapping.poToIdentity(user, claims.user().clientType()), claims.expiresAt(), claims.sessionId());
        Rotation rotation = sessions.rotate(login.user(), claims.sessionId(), refreshToken, login.refreshToken());
        if (rotation == Rotation.REPLAYED) {
            // 旧 refresh token 被重放：脚本已把整个会话吊销，持有者和真正用户都要重新登录
            LOG.warn("refresh token 重放，会话已吊销：userId={} clientType={}", user.getId(), claims.user().clientType());
        }
        return rotation == Rotation.ROTATED ? login : null;
    }

    private Login issue(Identity user, Instant refreshExpires, String sid) {
        Instant expires = Instant.now().plusSeconds(1800);
        if (expires.isAfter(refreshExpires)) expires = refreshExpires;
        return new Login(user, jwtTokens.issue(user, "access", expires, sid), expires,
                jwtTokens.issue(user, "refresh", refreshExpires, sid), refreshExpires);
    }
}
