package com.onsemi.cim.apps.exensio.dearchiver.repository;

import com.onsemi.cim.apps.exensio.dearchiver.entity.ExternalLocation;
import com.onsemi.cim.apps.exensio.dearchiver.entity.ExternalEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExternalLocationRepository extends JpaRepository<ExternalLocation, Long> {
    List<ExternalLocation> findByEnvironment(ExternalEnvironment environment);
}
