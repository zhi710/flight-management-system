package com.itemll.flight_management_system_sp.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 * <p>负责 Token 的生成、解析和验证。</p>
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.admin-expiration}")
    private long adminExpiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成旅客端 Token
     *
     * @param userId 用户ID
     * @param phone  手机号
     * @return JWT Token
     */
    public String generateToken(Long userId, String phone) {
        return buildToken(userId.toString(), phone, "USER", expiration);
    }

    /**
     * 生成管理端 Token
     *
     * @param adminId  管理员ID
     * @param username 用户名
     * @return JWT Token
     */
    public String generateAdminToken(Long adminId, String username) {
        return buildToken(adminId.toString(), username, "ADMIN", adminExpiration);
    }

    /**
     * 生成 Refresh Token
     */
    public String generateRefreshToken(Long userId) {
        return buildToken(userId.toString(), null, "REFRESH", refreshExpiration);
    }

    /**
     * 构建 Token
     */
    private String buildToken(String subject, String extra, String type, long expMs) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", type);
        if (extra != null) {
            claims.put("extra", extra);
        }

        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expMs))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token，返回 Claims
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中获取用户ID
     */
    public Long getUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    /**
     * 获取 Token 类型（USER/ADMIN/REFRESH）
     */
    public String getTokenType(String token) {
        return parseToken(token).get("type", String.class);
    }

    /**
     * 验证 Token 是否有效（未过期且签名正确）
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
