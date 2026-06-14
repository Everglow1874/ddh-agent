package com.ddh.agent.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.secret-key}")
    private String secretKey;

    @Value("${app.token-expire-minutes}")
    private int expireMinutes;

    public String generateToken(Long userId) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Jwts.builder()
            .setSubject(String.valueOf(userId))
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + expireMinutes * 60_000L))
            .signWith(Keys.hmacShaKeyFor(keyBytes), SignatureAlgorithm.HS256)
            .compact();
    }

    public Long extractUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(keyBytes))
            .build()
            .parseClaimsJws(token)
            .getBody();
    }
}
