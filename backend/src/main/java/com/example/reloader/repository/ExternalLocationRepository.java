package com.example.reloader.repository;

import com.example.reloader.entity.ExternalLocation;
import com.example.reloader.entity.ExternalEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExternalLocationRepository extends JpaRepository<ExternalLocation, Long> {
    List<ExternalLocation> findByEnvironment(ExternalEnvironment environment);
}
