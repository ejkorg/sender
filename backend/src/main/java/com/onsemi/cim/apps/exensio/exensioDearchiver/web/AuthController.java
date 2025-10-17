package com.onsemi.cim.apps.exensio.exensioDearchiver.web;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.RefreshToken;
import com.onsemi.cim.apps.exensio.exensioDearchiver.security.JwtUtil;
import com.onsemi.cim.apps.exensio.exensioDearchiver.service.RefreshTokenService;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.AuthRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.onsemi.cim.apps.exensio.exensioDearchiver.service.AuthTokenService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthenticationManager authManager, JwtUtil jwtUtil, RefreshTokenService refreshTokenService) {
        this.authManager = authManager;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
    }

    // --- verification / reset endpoints ---
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) return ResponseEntity.badRequest().build();
        // lookup verification token
        var authTokenService = getAuthTokenService();
        if (authTokenService == null) return ResponseEntity.status(500).build();
        var found = authTokenService.findVerificationToken(token);
        if (found.isEmpty()) return ResponseEntity.status(404).build();
        String username = found.get().getUsername();
        // enable the user
        enableUser(username);
        return ResponseEntity.ok(Map.of("message", "verified"));
    }

    @PostMapping("/request-reset")
    public ResponseEntity<Map<String, String>> requestReset(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.isBlank()) return ResponseEntity.badRequest().build();
        var authTokenService = getAuthTokenService();
        if (authTokenService == null) return ResponseEntity.status(500).build();
        var token = authTokenService.createPasswordResetToken(username);
        // in production we'd email the reset token; return for dev
        return ResponseEntity.ok(Map.of("resetToken", token.getToken()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("password");
        if (token == null || token.isBlank() || newPassword == null || newPassword.length() < 8) return ResponseEntity.badRequest().build();
        var authTokenService = getAuthTokenService();
        if (authTokenService == null) return ResponseEntity.status(500).build();
        var found = authTokenService.findPasswordResetToken(token);
        if (found.isEmpty()) return ResponseEntity.status(404).build();
        String username = found.get().getUsername();
        // update user password
        updatePassword(username, newPassword);
        return ResponseEntity.ok(Map.of("message", "password reset"));
    }

    // helper wiring via application context lookup to avoid constructor churn in this quick change
    private org.springframework.context.ApplicationContext getAppContext() {
        try {
            return org.springframework.web.context.ContextLoader.getCurrentWebApplicationContext();
        } catch (Exception e) {
            return null;
        }
    }

    private AuthTokenService getAuthTokenService() {
        var ctx = getAppContext();
        if (ctx == null) return null;
        return ctx.getBean(AuthTokenService.class);
    }

    private void enableUser(String username) {
        var ctx = getAppContext();
        if (ctx == null) return;
        var repo = ctx.getBean(com.onsemi.cim.apps.exensio.exensioDearchiver.repository.AppUserRepository.class);
        repo.findByUsername(username).ifPresent(u -> { u.setEnabled(true); repo.save(u); });
    }

    private void updatePassword(String username, String newPassword) {
        var ctx = getAppContext();
        if (ctx == null) return;
        var repo = ctx.getBean(com.onsemi.cim.apps.exensio.exensioDearchiver.repository.AppUserRepository.class);
        var encoder = ctx.getBean(org.springframework.security.crypto.password.PasswordEncoder.class);
        repo.findByUsername(username).ifPresent(u -> { u.setPasswordHash(encoder.encode(newPassword)); repo.save(u); });
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody AuthRequest req, HttpServletResponse resp) {
        Authentication a = authManager.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        String accessToken = jwtUtil.generateToken(req.getUsername());

        // create refresh token entity and set cookie
    RefreshToken rt = new RefreshToken();
    rt.setToken("refresh:" + System.currentTimeMillis());
    rt.setUsername(req.getUsername());
    rt.setExpiresAt(java.time.Instant.now().plusSeconds(60 * 60 * 24 * 7)); // 7 days
    refreshTokenService.save(rt);

    jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("refresh_token", rt.getToken());
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    resp.addCookie(cookie);

        Map<String, String> body = new HashMap<>();
        body.put("accessToken", accessToken);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(@CookieValue(value = "refresh_token", required = false) String refresh,
                                                      HttpServletResponse resp) {
        String incoming = refresh == null ? "" : refresh;
    logger.trace("[AuthController.refresh] incoming refresh cookie='{}'", incoming);
        Optional<RefreshToken> stored = refreshTokenService.findByToken(incoming);
        if (stored.isEmpty()) {
            logger.trace("[AuthController.refresh] no matching refresh token found for='{}'", incoming);
            return ResponseEntity.status(401).build();
        }

    // rotate
    refreshTokenService.revoke(stored.get());
    RefreshToken rt = new RefreshToken();
    rt.setToken("refresh:" + System.currentTimeMillis());
    rt.setUsername(stored.get().getUsername());
    rt.setExpiresAt(java.time.Instant.now().plusSeconds(60 * 60 * 24 * 7));
    refreshTokenService.save(rt);

        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("refresh_token", rt.getToken());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        resp.addCookie(cookie);

        Map<String, String> body = new HashMap<>();
        body.put("accessToken", jwtUtil.generateToken(rt.getUsername()));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = "refresh_token", required = false) String refresh, HttpServletResponse resp) {
        refreshTokenService.findByToken(refresh == null ? "" : refresh).ifPresent(rt -> {
            refreshTokenService.revoke(rt);
        });

        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("refresh_token", "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        resp.addCookie(cookie);
        return ResponseEntity.ok().build();
    }
}
