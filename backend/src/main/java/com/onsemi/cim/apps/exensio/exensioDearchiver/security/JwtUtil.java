package com.onsemi.cim.apps.exensio.exensioDearchiver.security;

import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtUtil {
    // NOTE: This is a minimal stub implementation for tests and local runs.
    // Replace with a proper JWT implementation (io.jsonwebtoken or similar).

    public String generateToken(String username) {
        // return a simple placeholder token
        return "token:" + username + ":" + System.currentTimeMillis();
    }

    public boolean validateToken(String token) {
        return token != null && token.startsWith("token:");
    }

    public String extractUsername(String token) {
        if (token == null) return null;
        String[] parts = token.split(":");
        return parts.length >= 2 ? parts[1] : null;
    }
}
