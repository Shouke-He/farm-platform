package com.farm.platform.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/** JWT 工具类：签发与解析 */
@Component
public class JwtUtil {

    // 签名密钥，来自 application.yml 的 farm.jwt.secret（具体值不在这里复述）；改动这个值会让所有已发出的 token 立即失效
    @Value("${farm.jwt.secret}")
    private String secret;

    @Value("${farm.jwt.expire-hours}")   // 令牌有效期（小时）
    private long expireHours;

    // 把配置里的字符串密钥转成 HMAC 密钥对象；密钥长度不足 256 位时 jjwt 会抛 WeakKeyException 拒绝启动签发
    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 token，载荷含 userId 与 roleId */
    public String generate(Long userId, Long roleId) {   // 【签令牌】
        Date now = new Date();
        return Jwts.builder()
                // subject 放用户 ID（解析方用 getSubject() 取回，就是"这张 token 属于谁"）
                .subject(String.valueOf(userId))
                // 自定义 claim：角色 ID，拦截器解析后在权限判断里用
                .claim("roleId", roleId)   // 【角色令牌】
                // 签发时间
                .issuedAt(now)
                // 过期时刻 = 当前毫秒 + 小时数 × 3600_000（expireHours 的单位是小时，这里换算成毫秒）
                .expiration(new Date(now.getTime() + expireHours * 3600_000L))
                // 用密钥签名（算法由密钥长度自动选定）
                .signWith(key())
                .compact();
    }

    /** 解析 token，失败抛异常 */
    public Claims parse(String token) {
        return Jwts.parser()
                // 用同一个密钥验签：签名不符、被篡改、已过期都会抛异常，由调用方转成 401
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
