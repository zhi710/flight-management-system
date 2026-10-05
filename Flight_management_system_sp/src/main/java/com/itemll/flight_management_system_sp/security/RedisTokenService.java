package com.itemll.flight_management_system_sp.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis Token 缓存服务
 * <p>将 Token 存储到 Redis 中，实现以下功能：</p>
 * <ul>
 *   <li>Token 登录态管理（支持主动失效/登出）</li>
 *   <li>防止 Token 重复使用</li>
 *   <li>支持 Token 刷新</li>
 * </ul>
 * <p>Redis Key 规范：</p>
 * <ul>
 *   <li>token:user:{userId} → 存储当前有效的旅客端 Token</li>
 *   <li>token:admin:{adminId} → 存储当前有效的管理端 Token</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisTokenService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TOKEN_USER_PREFIX = "token:user:";
    private static final String TOKEN_ADMIN_PREFIX = "token:admin:";

    /**
     * 保存旅客端 Token 到 Redis
     *
     * @param userId 用户ID
     * @param token  JWT Token
     * @param ttlMs  过期时间（毫秒）
     */
    public void saveUserToken(Long userId, String token, long ttlMs) {
        String key = TOKEN_USER_PREFIX + userId;
        redisTemplate.opsForValue().set(key, token, ttlMs, TimeUnit.MILLISECONDS);
        log.debug("保存旅客 Token: userId={}, ttl={}ms", userId, ttlMs);
    }

    /**
     * 保存管理端 Token 到 Redis
     */
    public void saveAdminToken(Long adminId, String token, long ttlMs) {
        String key = TOKEN_ADMIN_PREFIX + adminId;
        redisTemplate.opsForValue().set(key, token, ttlMs, TimeUnit.MILLISECONDS);
        log.debug("保存管理 Token: adminId={}, ttl={}ms", adminId, ttlMs);
    }

    /**
     * 获取旅客端 Token
     */
    public String getUserToken(Long userId) {
        String key = TOKEN_USER_PREFIX + userId;
        Object val = redisTemplate.opsForValue().get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * 获取管理端 Token
     */
    public String getAdminToken(Long adminId) {
        String key = TOKEN_ADMIN_PREFIX + adminId;
        Object val = redisTemplate.opsForValue().get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * 验证旅客端 Token 是否与 Redis 中存储的一致
     * <p>如果用户重新登录，旧 Token 会被覆盖，从而实现旧 Token 失效。</p>
     */
    public boolean validateUserToken(Long userId, String token) {
        String cached = getUserToken(userId);
        return token.equals(cached);
    }

    /**
     * 验证管理端 Token
     */
    public boolean validateAdminToken(Long adminId, String token) {
        String cached = getAdminToken(adminId);
        return token.equals(cached);
    }

    /**
     * 删除旅客端 Token（登出）
     */
    public void removeUserToken(Long userId) {
        redisTemplate.delete(TOKEN_USER_PREFIX + userId);
        log.debug("删除旅客 Token: userId={}", userId);
    }

    /**
     * 删除管理端 Token（登出）
     */
    public void removeAdminToken(Long adminId) {
        redisTemplate.delete(TOKEN_ADMIN_PREFIX + adminId);
        log.debug("删除管理 Token: adminId={}", adminId);
    }
}
