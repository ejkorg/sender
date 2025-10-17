package com.onsemi.cim.apps.exensio.exensioDearchiver.repository;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.ExternalLocation;
import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.ExternalEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExternalLocationRepository extends JpaRepository<ExternalLocation, Long> {
    List<ExternalLocation> findByEnvironment(ExternalEnvironment environment);
}
