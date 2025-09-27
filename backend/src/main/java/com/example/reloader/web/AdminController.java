package com.example.reloader.web;

import com.example.reloader.config.ExternalDbConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal")
public class AdminController {

    private final ExternalDbConfig externalDbConfig;
    private final MeterRegistry meterRegistry;

    public AdminController(ExternalDbConfig externalDbConfig, ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.externalDbConfig = externalDbConfig;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
    }

    @GetMapping("/pools")
    public ResponseEntity<Map<String, Object>> listPools() {
        return ResponseEntity.ok(externalDbConfig.listPoolStats());
    }

    @PostMapping("/pools/recreate")
    public ResponseEntity<String> recreatePool(@RequestParam String key) {
        try {
            externalDbConfig.recreatePool(key);
            return ResponseEntity.ok("recreated");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("error: " + e.getMessage());
        }
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics(@RequestParam(name = "includeMeters", defaultValue = "false") boolean includeMeters) {
        Set<String> active = externalDbConfig.getActivePoolKeys();
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.put("activePoolCount", active.size());
        out.put("activePools", active);
        int meterCount = meterRegistry == null ? 0 : meterRegistry.getMeters().size();
        out.put("meterCount", meterCount);
        if (includeMeters && meterRegistry != null) {
            out.put("meters", meterRegistry.getMeters().stream().map(m -> m.getId().getName()).collect(Collectors.toList()));
        }
        return ResponseEntity.ok(out);
    }
}
