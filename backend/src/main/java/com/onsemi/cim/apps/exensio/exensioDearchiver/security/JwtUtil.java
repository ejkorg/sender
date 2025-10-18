package com.onsemi.cim.apps.exensio.exensioDearchiver.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessTtlSeconds;

        public JwtUtil(
            @Value("${reloader.jwt.secret:dev-secret-change-me-please-32-bytes}") String secret,
            @Value("${reloader.jwt.access-ttl-seconds:900}") long accessTtlSeconds,
            @Value("${spring.profiles.active:}") String activeProfiles
        ) {
        // Support both base64 and plain secrets; Keys.hmacShaKeyFor requires >= 256-bit key for HS256
        byte[] bytes;
        try {
            bytes = java.util.Base64.getDecoder().decode(secret);
            if (bytes.length < 32) {
                // fallback to raw bytes if decoded too short
                bytes = secret.getBytes(StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException e) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            // pad to 32 bytes if needed (dev safety)
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, Math.min(bytes.length, 32));
            bytes = padded;
        }
        boolean isProd = activeProfiles != null && activeProfiles.contains("prod");
        String defaultSecret = "dev-secret-change-me-please-32-bytes";
        if (isProd) {
            // If in prod and secret is default or too short, fail fast
            boolean defaultUsed = defaultSecret.equals(secret);
            if (defaultUsed || bytes.length < 32) {
                throw new IllegalStateException("Invalid JWT secret in prod: configure reloader.jwt.secret to a strong 256-bit value (base64 or 32+ bytes)");
            }
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String generateToken(String username, Collection<String> roles) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + accessTtlSeconds * 1000);
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(exp)
                .addClaims(Map.of("roles", roles == null ? List.of() : List.copyOf(roles)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Backward-compatible convenience
    public String generateToken(String username) {
        return generateToken(username, List.of());
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getExpiration() == null || claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public List<String> extractRoles(String token) {
        try {
            Claims c = parseClaims(token);
            Object roles = c.get("roles");
            if (roles instanceof List<?>) {
                return ((List<?>) roles).stream().map(String::valueOf).collect(Collectors.toList());
            } else if (roles instanceof String) {
                return List.of((String) roles);
            } else {
                return List.of();
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }
}
