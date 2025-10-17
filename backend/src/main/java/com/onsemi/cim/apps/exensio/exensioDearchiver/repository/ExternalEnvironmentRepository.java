package com.onsemi.cim.apps.exensio.exensioDearchiver.repository;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.ExternalEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExternalEnvironmentRepository extends JpaRepository<ExternalEnvironment, Long> {
    Optional<ExternalEnvironment> findByName(String name);
}
