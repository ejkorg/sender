package com.example.reloader.repository;

import com.example.reloader.entity.ExternalEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExternalEnvironmentRepository extends JpaRepository<ExternalEnvironment, Long> {
    Optional<ExternalEnvironment> findByName(String name);
}
