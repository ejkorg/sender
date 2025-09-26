package com.example.reloader.service;

import com.example.reloader.entity.ExternalEnvironment;
import com.example.reloader.entity.ExternalLocation;
import com.example.reloader.repository.ExternalEnvironmentRepository;
import com.example.reloader.repository.ExternalLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ExternalLocationService {
    private final ExternalEnvironmentRepository envRepo;
    private final ExternalLocationRepository locRepo;

    public ExternalLocationService(ExternalEnvironmentRepository envRepo, ExternalLocationRepository locRepo) {
        this.envRepo = envRepo;
        this.locRepo = locRepo;
    }

    public List<ExternalEnvironment> listEnvironments() {
        return envRepo.findAll();
    }

    public List<ExternalLocation> listLocationsForEnvironment(String envName) {
        Optional<ExternalEnvironment> e = envRepo.findByName(envName);
        return e.map(locRepo::findByEnvironment).orElse(Collections.emptyList());
    }

    @Transactional
    public void importCsv(InputStream csvStream) throws IOException {
        // CSV format: environment, label, db_connection_name, details
        try (BufferedReader br = new BufferedReader(new InputStreamReader(csvStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",", 4);
                if (parts.length < 3) continue; // skip malformed
                String envName = parts[0].trim();
                String label = parts[1].trim();
                String dbConn = parts[2].trim();
                String details = parts.length >= 4 ? parts[3].trim() : null;

                ExternalEnvironment env = envRepo.findByName(envName).orElseGet(() -> {
                    ExternalEnvironment ne = new ExternalEnvironment();
                    ne.setName(envName);
                    ne.setDescription(null);
                    return envRepo.save(ne);
                });

                // Avoid duplicates by label+env
                List<ExternalLocation> existing = locRepo.findByEnvironment(env);
                boolean found = existing.stream().anyMatch(l -> l.getLabel().equals(label) && l.getDbConnectionName().equals(dbConn));
                if (!found) {
                    ExternalLocation loc = new ExternalLocation();
                    loc.setEnvironment(env);
                    loc.setLabel(label);
                    loc.setDbConnectionName(dbConn);
                    loc.setDetails(details);
                    // Derive site: first token of label (split on space), uppercase
                    String siteVal = label;
                    if (siteVal != null && !siteVal.isBlank()) {
                        String[] tokens = siteVal.trim().split("\\s+", 2);
                        siteVal = tokens[0].toUpperCase();
                    } else {
                        siteVal = null;
                    }
                    loc.setSite(siteVal);
                    locRepo.save(loc);
                }
            }
        }
    }
}
