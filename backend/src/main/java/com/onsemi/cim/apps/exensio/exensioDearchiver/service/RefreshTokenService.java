package com.onsemi.cim.apps.exensio.exensioDearchiver.service;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.RefreshToken;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenService.class);

    public RefreshTokenService(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    public Optional<RefreshToken> findByToken(String token) {
    logger.trace("[RefreshTokenService.findByToken] lookup token='{}'", token);
        Optional<RefreshToken> opt = repo.findByToken(token);
        if (opt.isPresent()) {
            RefreshToken t = opt.get();
            logger.trace("[RefreshTokenService.findByToken] found token revoked={} username={}", t.isRevoked(), t.getUsername());
        } else {
            logger.trace("[RefreshTokenService.findByToken] token not found");
        }
        return opt.filter(t -> !t.isRevoked());
    }

    public RefreshToken save(RefreshToken token) { return repo.save(token); }

    public void revoke(RefreshToken token) {
        token.setRevoked(true);
        repo.save(token);
    }
}
