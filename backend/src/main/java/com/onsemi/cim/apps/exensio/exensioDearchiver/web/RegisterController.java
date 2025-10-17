package com.onsemi.cim.apps.exensio.exensioDearchiver.web;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.AppUser;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.AppUserRepository;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.RegisterRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class RegisterController {

    private final AppUserRepository repo;
    private final PasswordEncoder encoder;

    private final AuthTokenService tokenService;

    public RegisterController(AppUserRepository repo, PasswordEncoder encoder, AuthTokenService tokenService) {
        this.repo = repo;
        this.encoder = encoder;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest req) {
        if (req.getUsername() == null || req.getUsername().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "username required"));
        }
        if (req.getPassword() == null || req.getPassword().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "password must be at least 8 characters"));
        }
        if (repo.findByUsername(req.getUsername()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "username already exists"));
        }
        if (req.getEmail() != null && repo.findByEmail(req.getEmail()).isPresent()) {
            return ResponseEntity.status(409).body(Map.of("error", "email already exists"));
        }

        AppUser u = new AppUser();
        u.setUsername(req.getUsername());
        u.setEmail(req.getEmail());
        u.setPasswordHash(encoder.encode(req.getPassword()));
        u.getRoles().add("ROLE_USER");
        u.setEnabled(true);
        repo.save(u);

        // create verification token - in production we'd send this via email.
        var vt = tokenService.createVerificationToken(u.getUsername());
        Map<String, String> body = new HashMap<>();
        body.put("message", "registered");
        body.put("verificationToken", vt.getToken()); // dev helper
        return ResponseEntity.ok(body);
    }
}
