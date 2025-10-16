package com.onsemi.cim.apps.exensio.dearchiver.repository;

import com.onsemi.cim.apps.exensio.dearchiver.entity.ExternalEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExternalEnvironmentRepository extends JpaRepository<ExternalEnvironment, Long> {
    Optional<ExternalEnvironment> findByName(String name);
}
