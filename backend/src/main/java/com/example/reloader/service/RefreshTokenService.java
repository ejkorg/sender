package com.example.reloader.service;

import com.example.reloader.entity.RefreshToken;
import com.example.reloader.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;

    public RefreshTokenService(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    public Optional<RefreshToken> findByToken(String token) {
        return repo.findByToken(token).filter(t -> !t.isRevoked());
    }

    public RefreshToken save(RefreshToken token) { return repo.save(token); }

    public void revoke(RefreshToken token) {
        token.setRevoked(true);
        repo.save(token);
    }
}
