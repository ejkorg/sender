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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.onsemi.cim.apps.exensio.exensioDearchiver.service.AuthTokenService;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;
    private final AuthTokenService authTokenService;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.onsemi.cim.apps.exensio.exensioDearchiver.service.MailService mailService;
    private final boolean returnTokensInResponse;
    private final String resetUrlBase;
    // cookie attributes for refresh token; configurable via application properties
    @org.springframework.beans.factory.annotation.Value("${reloader.refresh.cookie-secure:false}")
    private boolean refreshCookieSecure;

    @org.springframework.beans.factory.annotation.Value("${reloader.refresh.cookie-sameSite:None}")
    private String refreshCookieSameSite;

    public AuthController(AuthenticationManager authManager, JwtUtil jwtUtil, RefreshTokenService refreshTokenService,
                          AuthTokenService authTokenService, AppUserRepository userRepository, PasswordEncoder passwordEncoder,
                          com.onsemi.cim.apps.exensio.exensioDearchiver.service.MailService mailService,
                          @org.springframework.beans.factory.annotation.Value("${app.mail.reset-url-base:}") String resetUrlBase,
                          @org.springframework.beans.factory.annotation.Value("${reloader.auth.return-tokens-in-response:true}") boolean returnTokensInResponse) {
        this.authManager = authManager;
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
        this.authTokenService = authTokenService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.resetUrlBase = resetUrlBase == null ? "" : resetUrlBase;
        this.returnTokensInResponse = returnTokensInResponse;
    }

    // --- verification / reset endpoints ---
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verify(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        if (token == null || token.isBlank()) return ResponseEntity.badRequest().build();
        // lookup verification token
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

        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            logger.debug("Password reset requested for unknown user '{}'; returning generic success", username);
            return ResponseEntity.ok(Map.of("message", "reset requested"));
        }

        var token = authTokenService.createPasswordResetToken(username);
        // attempt to send email if user has an email address configured
        try {
            String to = userOpt.get().getEmail();
            if (to == null || to.isBlank()) {
                logger.info("Password reset requested for user='{}' but no email is configured; skipping send", username);
            } else {
                logger.info("Password reset requested for user='{}' email='{}' - attempting to send reset email", username, to);
                String subject = "Password reset request";
                String bodyText;
                if (this.resetUrlBase != null && !this.resetUrlBase.isBlank()) {
                    // append token as query parameter
                    String sep = this.resetUrlBase.contains("?") ? "&" : "?";
                    bodyText = "Reset your password using the following link:\n" + this.resetUrlBase + sep + "token=" + token.getToken();
                } else {
                    bodyText = "Use this token to reset your password: " + token.getToken();
                }
                mailService.send(to, subject, bodyText);
                logger.info("MailService.send invoked for user='{}' email='{}'", username, to);
            }
        } catch (Exception e) {
            logger.warn("Failed to send reset email for user={}", username, e);
        }
        // return token in response only when explicitly enabled (tests/dev). In prod this should be disabled.
        if (this.returnTokensInResponse) {
            return ResponseEntity.ok(Map.of("resetToken", token.getToken()));
        } else {
            return ResponseEntity.ok(Map.of("message", "reset requested"));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("password");
        if (token == null || token.isBlank() || newPassword == null || newPassword.length() < 8) return ResponseEntity.badRequest().build();
        var found = authTokenService.findPasswordResetToken(token);
        if (found.isEmpty()) return ResponseEntity.status(404).build();
        String username = found.get().getUsername();
        // update user password
        updatePassword(username, newPassword);
        // revoke all refresh tokens to force re-login on all devices
        try {
            refreshTokenService.revokeAllForUser(username);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of("message", "password reset"));
    }

    private void enableUser(String username) {
        userRepository.findByUsername(username).ifPresent(u -> { u.setEnabled(true); userRepository.save(u); });
    }

    private void updatePassword(String username, String newPassword) {
        userRepository.findByUsername(username).ifPresent(u -> { u.setPasswordHash(passwordEncoder.encode(newPassword)); userRepository.save(u); });
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody AuthRequest req, HttpServletResponse resp) {
        Authentication a;
        try {
            a = authManager.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
        try {
            java.util.List<String> roles = a.getAuthorities().stream().map(granted -> granted.getAuthority()).toList();
            String accessToken = jwtUtil.generateToken(req.getUsername(), roles);

            // create refresh token entity and set cookie
            RefreshToken rt = new RefreshToken();
            rt.setToken("refresh:" + System.currentTimeMillis());
            rt.setUsername(req.getUsername());
            rt.setExpiresAt(java.time.Instant.now().plusSeconds(60 * 60 * 24 * 7)); // 7 days
            refreshTokenService.save(rt);

            // Build Set-Cookie header manually so we can control SameSite and Secure attrs
            StringBuilder sc = new StringBuilder();
            sc.append("refresh_token=").append(rt.getToken()).append("; Path=/; HttpOnly");
            if (this.refreshCookieSecure) sc.append("; Secure");
            if (this.refreshCookieSameSite != null && !this.refreshCookieSameSite.isBlank()) {
                String s = this.refreshCookieSameSite.trim();
                if ("None".equalsIgnoreCase(s) && !this.refreshCookieSecure) {
                    logger.warn("[AuthController.login] SameSite=None configured but refreshCookieSecure=false; omitting SameSite attribute to avoid browser rejection in non-secure dev contexts");
                } else {
                    sc.append("; SameSite=").append(s);
                }
            }
            resp.addHeader("Set-Cookie", sc.toString());

            Map<String, String> body = new HashMap<>();
            body.put("accessToken", accessToken);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("[AuthController.login] unexpected error while creating tokens for user={}", req.getUsername(), e);
            return ResponseEntity.status(500).body(Map.of("error", "internal server error"));
        }
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
        try {
            // rotate
            refreshTokenService.revoke(stored.get());
            RefreshToken rt = new RefreshToken();
            rt.setToken("refresh:" + System.currentTimeMillis());
            rt.setUsername(stored.get().getUsername());
            rt.setExpiresAt(java.time.Instant.now().plusSeconds(60 * 60 * 24 * 7));
            refreshTokenService.save(rt);

            // Build Set-Cookie header manually for refresh rotation
            StringBuilder sc2 = new StringBuilder();
            sc2.append("refresh_token=").append(rt.getToken()).append("; Path=/; HttpOnly");
            if (this.refreshCookieSecure) sc2.append("; Secure");
            if (this.refreshCookieSameSite != null && !this.refreshCookieSameSite.isBlank()) {
                String s2 = this.refreshCookieSameSite.trim();
                if ("None".equalsIgnoreCase(s2) && !this.refreshCookieSecure) {
                    logger.warn("[AuthController.refresh] SameSite=None configured but refreshCookieSecure=false; omitting SameSite attribute to avoid browser rejection in non-secure dev contexts");
                } else {
                    sc2.append("; SameSite=").append(s2);
                }
            }
            resp.addHeader("Set-Cookie", sc2.toString());

            Map<String, String> body = new HashMap<>();
            // On refresh we do not have Authentication; rebuild roles from DB
            java.util.List<String> roles = userRepository.findByUsername(rt.getUsername())
                .map(u -> new java.util.ArrayList<String>(u.getRoles()))
                .orElse(new java.util.ArrayList<>());
            body.put("accessToken", jwtUtil.generateToken(rt.getUsername(), roles));
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            logger.error("[AuthController.refresh] unexpected error rotating refresh token for='{}'", incoming, e);
            return ResponseEntity.status(500).body(Map.of("error", "internal server error"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = "refresh_token", required = false) String refresh, HttpServletResponse resp) {
        refreshTokenService.findByToken(refresh == null ? "" : refresh).ifPresent(rt -> {
            refreshTokenService.revoke(rt);
        });

        // Clear cookie by setting an expired Set-Cookie header
        StringBuilder sc3 = new StringBuilder();
        sc3.append("refresh_token=; Path=/; HttpOnly; Max-Age=0");
        if (this.refreshCookieSecure) sc3.append("; Secure");
        if (this.refreshCookieSameSite != null && !this.refreshCookieSameSite.isBlank()) {
            String s3 = this.refreshCookieSameSite.trim();
            if ("None".equalsIgnoreCase(s3) && !this.refreshCookieSecure) {
                logger.warn("[AuthController.logout] SameSite=None configured but refreshCookieSecure=false; omitting SameSite attribute to avoid browser rejection in non-secure dev contexts");
            } else {
                sc3.append("; SameSite=").append(s3);
            }
        }
        resp.addHeader("Set-Cookie", sc3.toString());
        return ResponseEntity.ok().build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(org.springframework.security.core.Authentication authentication) {
        // server-side debug: log presence of Authorization header and refresh cookie (masked)
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                var req = attrs.getRequest();
                String authHeader = req.getHeader("Authorization");
                String authMasked = null;
                if (authHeader != null && !authHeader.isBlank()) {
                    authMasked = authHeader.length() > 12 ? authHeader.substring(0, 6) + "…" + authHeader.substring(authHeader.length() - 4) : authHeader;
                }
                String cookieMask = "(none)";
                var cookies = req.getCookies();
                if (cookies != null) {
                    for (var c : cookies) {
                        if ("refresh_token".equals(c.getName())) {
                            String v = c.getValue();
                            cookieMask = v == null ? "(empty)" : (v.length() > 12 ? v.substring(0, 6) + "…" + v.substring(v.length() - 4) : v);
                            break;
                        }
                    }
                }
                logger.debug("[AuthController.me] incoming Authorization={} refresh_cookie={}", authMasked, cookieMask);
            }
        } catch (Exception e) {
            logger.warn("[AuthController.me] failed to inspect incoming request headers", e);
        }

        if (authentication == null || !authentication.isAuthenticated()) return ResponseEntity.status(401).build();
        Map<String, Object> body = new HashMap<>();
        body.put("username", authentication.getName());
        body.put("roles", authentication.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toList()));
        return ResponseEntity.ok(body);
    }
}
