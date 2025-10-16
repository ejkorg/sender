package com.onsemi.cim.apps.exensio.dearchiver.web;

import com.onsemi.cim.apps.exensio.dearchiver.config.ExternalDbConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/external")
public class ExternalConnectionsController {
    private final ExternalDbConfig externalDbConfig;

    public ExternalConnectionsController(ExternalDbConfig externalDbConfig) {
        this.externalDbConfig = externalDbConfig;
    }

    // Return list of base connection keys available for a given environment (qa|prod)
    // Parses keys like KEY-qa, KEY_qa, KEY.qa; dedupes by base key
    @GetMapping("/instances")
    public ResponseEntity<List<Map<String,String>>> listInstances(@RequestParam("environment") String environment) {
        if (environment == null || environment.isBlank()) environment = "qa";
        environment = environment.toLowerCase(Locale.ROOT);
        Set<String> bases = new TreeSet<>();
        for (String key : externalDbConfig.getConfiguredKeys()) {
            String lower = key.toLowerCase(Locale.ROOT);
            String base = null;
            if (lower.endsWith("-"+environment)) base = key.substring(0, key.length() - (environment.length()+1));
            else if (lower.endsWith("_"+environment)) base = key.substring(0, key.length() - (environment.length()+1));
            else if (lower.endsWith("."+environment)) base = key.substring(0, key.length() - (environment.length()+1));
            else {
                // No explicit suffix; include if it resolves for this environment (fallback variant exists)
                if (externalDbConfig.getConfigForSite(key, environment) != null) base = key;
            }
            if (base != null && !base.isBlank()) bases.add(base);
        }
        List<Map<String,String>> out = new ArrayList<>();
        for (String b : bases) {
            out.add(Map.of("key", b, "label", b, "environment", environment));
        }
        return ResponseEntity.ok(out);
    }
}
