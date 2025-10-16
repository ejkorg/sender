package com.onsemi.cim.apps.exensio.dearchiver.web;

import com.onsemi.cim.apps.exensio.dearchiver.entity.RefreshToken;
import com.onsemi.cim.apps.exensio.dearchiver.security.JwtUtil;
import com.onsemi.cim.apps.exensio.dearchiver.service.RefreshTokenService;
import com.onsemi.cim.apps.exensio.dearchiver.web.dto.AuthRequest;
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
