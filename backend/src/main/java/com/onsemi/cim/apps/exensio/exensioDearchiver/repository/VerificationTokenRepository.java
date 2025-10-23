package com.onsemi.cim.apps.exensio.exensioDearchiver.repository;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByToken(String token);
}
