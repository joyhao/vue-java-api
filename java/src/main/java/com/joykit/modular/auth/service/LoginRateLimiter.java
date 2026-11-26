package com.joykit.modular.auth.service;

import com.joykit.common.exception.BusinessException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 登录 / 注册限流，防匿名接口被用来打满 CPU（每次登录都执行 PBKDF2）和爆破密码。
 * 计数器存 Redis，两个维度：
 * - IP 维度滑动窗口：限制单位时间内的尝试次数；
 * - 账号维度失败锁定：连续失败达到阈值后一段时间内直接拒绝。
 * Redis 不可用时放行（fail-open）：限流是附加防护，不能反过来把登录打成不可用。
 */
@Component
public class LoginRateLimiter {
    private static final Logger LOG = LoggerFactory.getLogger(LoginRateLimiter.class);

    static final int LOGIN_IP_LIMIT = 10;
    static final Duration LOGIN_IP_WINDOW = Duration.ofMinutes(1);
    static final int FAIL_LOCK_THRESHOLD = 5;
    static final Duration FAIL_LOCK_WINDOW = Duration.ofMinutes(15);
    static final int REGISTER_IP_LIMIT = 5;
    static final Duration REGISTER_IP_WINDOW = Duration.ofHours(1);
    /** Redis 告警的抑制窗口，避免停机期间每个请求都打一条日志 */
    private static final long WARN_SUPPRESS_MS = 10_000;

    private final StringRedisTemplate redis;
    private volatile long warnSuppressUntil;

    public LoginRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 登录前调用：账号已锁定或 IP 超过尝试上限时抛 RATE_LIMITED */
    public void checkLoginAllowed(String ip, String account) {
        try {
            long failures = read(failKey(account));
            if (failures >= FAIL_LOCK_THRESHOLD) {
                throw new BusinessException(BusinessException.Kind.RATE_LIMITED,
                        "登录失败次数过多，账号已临时锁定，请 " + FAIL_LOCK_WINDOW.toMinutes() + " 分钟后再试");
            }
            if (attempt(loginIpKey(ip), LOGIN_IP_WINDOW) > LOGIN_IP_LIMIT) {
                throw new BusinessException(BusinessException.Kind.RATE_LIMITED,
                        "登录尝试过于频繁，请稍后再试");
            }
        } catch (DataAccessException exception) {
            degrade(exception);
        }
    }

    /** 登录失败后调用：累计该账号的失败次数 */
    public void recordLoginFailure(String ip, String account) {
        try {
            attempt(failKey(account), FAIL_LOCK_WINDOW);
        } catch (DataAccessException exception) {
            degrade(exception);
        }
    }

    /** 登录成功后调用：清空失败计数，重新计下次数 */
    public void recordLoginSuccess(String account) {
        try {
            redis.delete(failKey(account));
        } catch (DataAccessException exception) {
            degrade(exception);
        }
    }

    /** 注册前调用：同一 IP 注册频率超限时抛 RATE_LIMITED */
    public void checkRegisterAllowed(String ip) {
        try {
            if (attempt(registerIpKey(ip), REGISTER_IP_WINDOW) > REGISTER_IP_LIMIT) {
                throw new BusinessException(BusinessException.Kind.RATE_LIMITED,
                        "注册过于频繁，请稍后再试");
            }
        } catch (DataAccessException exception) {
            degrade(exception);
        }
    }

    /** 计数一次，并在首次计数时设置过期时间（窗口内不再重置） */
    private long attempt(String key, Duration window) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) redis.expire(key, window);
        return count == null ? 0L : count;
    }

    private long read(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private void degrade(DataAccessException exception) {
        if (System.currentTimeMillis() < warnSuppressUntil) return;
        warnSuppressUntil = System.currentTimeMillis() + WARN_SUPPRESS_MS;
        LOG.warn("Redis 不可用，登录限流暂时失效（放行）", exception);
    }

    private static String failKey(String account) { return "joykit:auth:rl:fail:" + account.toLowerCase(); }

    private static String loginIpKey(String ip) { return "joykit:auth:rl:login-ip:" + ip; }

    private static String registerIpKey(String ip) { return "joykit:auth:rl:register-ip:" + ip; }
}
