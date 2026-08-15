package com.autoparts.inventory.security;

import com.autoparts.inventory.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {
    private final SecretKey key;
    private final int accessExpiryHours;

    public JwtService(AppProperties props) {
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(props.jwt().secret());
        } catch (IllegalArgumentException ex) {
            secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        }
        if (secret.length < 32) {
            throw new IllegalStateException("jwt secret must decode to at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessExpiryHours = props.jwt().accessExpiryHours();
    }

    public String generateAccessToken(UUID userId, String phone) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("phone", phone)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessExpiryHours * 3600L)))
                .signWith(key)
                .compact();
    }

    public UUID parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }
}
