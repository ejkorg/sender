package com.onsemi.cim.apps.exensio.exensioDearchiver.service;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.PasswordResetToken;
import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.VerificationToken;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.PasswordResetTokenRepository;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.VerificationTokenRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthTokenService {

    private final VerificationTokenRepository verificationRepo;
    private final PasswordResetTokenRepository resetRepo;

    public AuthTokenService(VerificationTokenRepository verificationRepo, PasswordResetTokenRepository resetRepo) {
        this.verificationRepo = verificationRepo;
        this.resetRepo = resetRepo;
    }

    public VerificationToken createVerificationToken(String username) {
        VerificationToken t = new VerificationToken();
        t.setToken(UUID.randomUUID().toString());
        t.setUsername(username);
        t.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        verificationRepo.save(t);
        return t;
    }

    public Optional<VerificationToken> findVerificationToken(String token) {
        return verificationRepo.findByToken(token).filter(t -> t.getExpiresAt().isAfter(Instant.now()));
    }

    public PasswordResetToken createPasswordResetToken(String username) {
        PasswordResetToken t = new PasswordResetToken();
        t.setToken(UUID.randomUUID().toString());
        t.setUsername(username);
        t.setExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS));
        resetRepo.save(t);
        return t;
    }

    public Optional<PasswordResetToken> findPasswordResetToken(String token) {
        return resetRepo.findByToken(token).filter(t -> t.getExpiresAt().isAfter(Instant.now()));
    }
}
