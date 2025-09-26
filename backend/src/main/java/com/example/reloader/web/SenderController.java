package com.example.reloader.web;

import com.example.reloader.entity.SenderQueueEntry;
import com.example.reloader.repository.SenderQueueRepository;
import com.example.reloader.service.SenderService;
import com.example.reloader.web.dto.EnqueueRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/senders")
public class SenderController {
    private final SenderService senderService;
    private final SenderQueueRepository repo;
    private final com.example.reloader.service.MetadataImporterService metadataImporterService;
    private final com.example.reloader.service.MetricsService metricsService;
    public SenderController(SenderService senderService, SenderQueueRepository repo, com.example.reloader.service.MetadataImporterService metadataImporterService, com.example.reloader.service.MetricsService metricsService) {
        this.senderService = senderService;
        this.repo = repo;
        this.metadataImporterService = metadataImporterService;
        this.metricsService = metricsService;
    }

    @GetMapping("/{id}/queue")
    public List<SenderQueueEntry> getQueue(@PathVariable("id") Integer id,
                                           @RequestParam(defaultValue = "NEW") String status,
                                           @RequestParam(defaultValue = "100") int limit) {
        return senderService.getQueue(id, status, limit);
    }

    // Run now: triggers scheduled logic immediately for testing / on-demand
    @PostMapping("/{id}/run")
    public ResponseEntity<String> runNow(@PathVariable("id") Integer id,
                                         @RequestParam(defaultValue = "false") boolean preview,
                                         @RequestParam(defaultValue = "100") int limit) {
        if (preview) {
            return ResponseEntity.ok("Preview - no items processed");
        }
        senderService.processBatch(limit);
        return ResponseEntity.accepted().body("Run started");
    }

    // New endpoint: enqueue payloads from UI form (Reload / Submit)
    @PostMapping("/{id}/enqueue")
    public ResponseEntity<com.example.reloader.web.dto.EnqueueResult> enqueue(@PathVariable("id") Integer id, @RequestBody EnqueueRequest req) {
        Integer senderId = id;
        if (req.getSenderId() != null) senderId = req.getSenderId();
        SenderService.EnqueueResultHolder holder = senderService.enqueuePayloadsWithResult(senderId, req.getPayloadIds(), req.getSource() != null ? req.getSource() : "ui_submit");
        com.example.reloader.web.dto.EnqueueResult result = new com.example.reloader.web.dto.EnqueueResult(holder.enqueuedCount, holder.skippedPayloads);
        return ResponseEntity.ok(result);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('USER')")
    @PostMapping("/{id}/discover")
    public ResponseEntity<String> discover(@PathVariable("id") Integer id,
                                           @RequestParam(defaultValue = "default") String site,
                                           @RequestParam(defaultValue = "qa") String environment,
                                           @RequestParam(required = false) String startDate,
                                           @RequestParam(required = false) String endDate,
                                           @RequestParam(required = false) String testerType,
                                           @RequestParam(required = false) String dataType,
                                           @RequestParam(required = false) String testPhase,
                                           // legacy 'location' parameter is the metadata filter (kept for compatibility)
                                           @RequestParam(required = false) String location,
                                           // new explicit parameter name to clarify intent: the metadata filter to use in the external query
                                           @RequestParam(required = false, name = "metadataLocation") String metadataLocation,
                                           // the saved ExternalLocation id which selects which DB connection to use
                                           @RequestParam(required = false, name = "locationId") Long locationId,
                                           @RequestParam(defaultValue = "false") boolean writeListFile,
                                           @RequestParam(defaultValue = "300") int numberOfDataToSend,
                                           @RequestParam(defaultValue = "600") int countLimitTrigger) {

        // Prefer explicit metadataLocation if supplied; fall back to legacy 'location' param for compatibility.
        String filterLocation = metadataLocation != null ? metadataLocation : location;

        // If a saved locationId is provided and no explicit site was supplied, try to set 'site' from the saved ExternalLocation
        if (locationId != null && (site == null || site.equals("default") || site.isBlank())) {
            com.example.reloader.entity.ExternalLocation loc = metadataImporterService.findLocationById(locationId);
            if (loc != null && loc.getSite() != null && !loc.getSite().isBlank()) {
                site = loc.getSite();
            }
        }

        int added = metadataImporterService.discoverAndEnqueue(site, environment, id, startDate, endDate, testerType, dataType, testPhase, filterLocation, locationId, writeListFile, numberOfDataToSend, countLimitTrigger);
        return ResponseEntity.ok("Discovered and enqueued " + added + " payloads");
    }

    // Lookup senders in selected external DB based on user-provided filters. Returns list of {idSender,name}
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/lookup")
    public ResponseEntity<java.util.List<java.util.Map<String,Object>>> lookupSenders(@RequestParam(defaultValue = "default") String site,
                                                                                       @RequestParam(defaultValue = "qa") String environment,
                                                                                       @RequestParam(required = false) String metadataLocation,
                                                                                       @RequestParam(required = false) String dataType,
                                                                                       @RequestParam(required = false) String testerType,
                                                                                       @RequestParam(required = false) String testPhase,
                                                                                       // saved ExternalLocation id used to select which DB connection to use
                                                                                       @RequestParam(required = false, name = "locationId") Long locationId,
                                                                                       // alternatively, allow callers to provide the connection key directly
                                                                                       @RequestParam(required = false, name = "connectionKey") String connectionKey) {
        try {
            com.example.reloader.entity.ExternalLocation loc = null;
            java.sql.Connection conn = null;
            if (locationId != null) {
                loc = metadataImporterService.findLocationById(locationId);
                if (loc == null) throw new IllegalArgumentException("locationId not found");
                conn = metadataImporterService.resolveConnectionForLocation(loc, environment);
            } else if (connectionKey != null && !connectionKey.isBlank()) {
                conn = metadataImporterService.resolveConnectionForKey(connectionKey, environment);
            } else {
                throw new IllegalArgumentException("locationId or connectionKey is required");
            }

            try (java.sql.Connection c = conn) {
                // metric: record lookup by saved connection key or locationId
                String key = loc != null ? ("locationId=" + loc.getId()) : connectionKey;
                try { metricsService.increment("external.lookup", key); } catch (Exception ignore) {}
                java.util.List<com.example.reloader.repository.SenderCandidate> res = metadataImporterService.findSendersWithConnection(c, metadataLocation, dataType, testerType, testPhase);
                    java.util.List<java.util.Map<String,Object>> out = new java.util.ArrayList<>();
                    for (com.example.reloader.repository.SenderCandidate s : res) {
                        java.util.Map<String,Object> m = new java.util.HashMap<>();
                        m.put("idSender", s.getIdSender());
                        m.put("name", s.getName());
                        out.add(m);
                    }
                return ResponseEntity.ok(out);
            }
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(java.util.Collections.singletonList(java.util.Map.of("error", ex.getMessage())));
        }
    }

    // Fetch distinct location values from the selected external DB (for location dropdown)
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/external/locations")
    public ResponseEntity<java.util.List<String>> externalDistinctLocations(@RequestParam(required = false, name = "locationId") Long locationId,
                                                                              @RequestParam(required = false, name = "connectionKey") String connectionKey,
                                                                              @RequestParam(required = false) String dataType,
                                                                              @RequestParam(required = false) String testerType,
                                                                              @RequestParam(required = false) String testPhase,
                                                                              @RequestParam(defaultValue = "qa") String environment) {
        try {
            java.sql.Connection conn = null;
            if (locationId != null) {
                com.example.reloader.entity.ExternalLocation loc = metadataImporterService.findLocationById(locationId);
                if (loc == null) throw new IllegalArgumentException("locationId not found");
                conn = metadataImporterService.resolveConnectionForLocation(loc, environment);
            } else if (connectionKey != null && !connectionKey.isBlank()) {
                conn = metadataImporterService.resolveConnectionForKey(connectionKey, environment);
            } else {
                throw new IllegalArgumentException("locationId or connectionKey is required");
            }
            try (java.sql.Connection c = conn) {
                try { metricsService.increment("external.locations", locationId != null ? "locationId=" + locationId : connectionKey); } catch (Exception ignore) {}
                java.util.List<String> out = metadataImporterService.findDistinctLocationsWithConnection(c, dataType, testerType, testPhase);
                return ResponseEntity.ok(out);
            }
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(java.util.List.of());
        }
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/external/dataTypes")
    public ResponseEntity<java.util.List<String>> externalDistinctDataTypes(@RequestParam(required = false, name = "locationId") Long locationId,
                                                                             @RequestParam(required = false, name = "connectionKey") String connectionKey,
                                                                             @RequestParam(required = false) String location,
                                                                             @RequestParam(required = false) String testerType,
                                                                             @RequestParam(required = false) String testPhase,
                                                                             @RequestParam(defaultValue = "qa") String environment) {
        try {
            java.sql.Connection conn = null;
            if (locationId != null) {
                com.example.reloader.entity.ExternalLocation loc = metadataImporterService.findLocationById(locationId);
                if (loc == null) throw new IllegalArgumentException("locationId not found");
                conn = metadataImporterService.resolveConnectionForLocation(loc, environment);
            } else if (connectionKey != null && !connectionKey.isBlank()) {
                conn = metadataImporterService.resolveConnectionForKey(connectionKey, environment);
            } else {
                throw new IllegalArgumentException("locationId or connectionKey is required");
            }
            try (java.sql.Connection c = conn) {
                try { metricsService.increment("external.dataTypes", locationId != null ? "locationId=" + locationId : connectionKey); } catch (Exception ignore) {}
                java.util.List<String> out = metadataImporterService.findDistinctDataTypesWithConnection(c, location, testerType, testPhase);
                return ResponseEntity.ok(out);
            }
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(java.util.List.of());
        }
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/external/testerTypes")
    public ResponseEntity<java.util.List<String>> externalDistinctTesterTypes(@RequestParam(required = false, name = "locationId") Long locationId,
                                                                               @RequestParam(required = false, name = "connectionKey") String connectionKey,
                                                                               @RequestParam(required = false) String location,
                                                                               @RequestParam(required = false) String dataType,
                                                                               @RequestParam(required = false) String testPhase,
                                                                               @RequestParam(defaultValue = "qa") String environment) {
        try {
            java.sql.Connection conn = null;
            if (locationId != null) {
                com.example.reloader.entity.ExternalLocation loc = metadataImporterService.findLocationById(locationId);
                if (loc == null) throw new IllegalArgumentException("locationId not found");
                conn = metadataImporterService.resolveConnectionForLocation(loc, environment);
            } else if (connectionKey != null && !connectionKey.isBlank()) {
                conn = metadataImporterService.resolveConnectionForKey(connectionKey, environment);
            } else {
                throw new IllegalArgumentException("locationId or connectionKey is required");
            }
            try (java.sql.Connection c = conn) {
                try { metricsService.increment("external.testerTypes", locationId != null ? "locationId=" + locationId : connectionKey); } catch (Exception ignore) {}
                java.util.List<String> out = metadataImporterService.findDistinctTesterTypesWithConnection(c, location, dataType, testPhase);
                return ResponseEntity.ok(out);
            }
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(java.util.List.of());
        }
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/external/testPhases")
    public ResponseEntity<java.util.List<String>> externalDistinctTestPhases(@RequestParam(required = false, name = "locationId") Long locationId,
                                                                             @RequestParam(required = false, name = "connectionKey") String connectionKey,
                                                                             @RequestParam(required = false) String location,
                                                                             @RequestParam(required = false) String dataType,
                                                                             @RequestParam(required = false) String testerType,
                                                                             @RequestParam(defaultValue = "qa") String environment) {
        try {
            java.sql.Connection conn = null;
            if (locationId != null) {
                com.example.reloader.entity.ExternalLocation loc = metadataImporterService.findLocationById(locationId);
                if (loc == null) throw new IllegalArgumentException("locationId not found");
                conn = metadataImporterService.resolveConnectionForLocation(loc, environment);
            } else if (connectionKey != null && !connectionKey.isBlank()) {
                conn = metadataImporterService.resolveConnectionForKey(connectionKey, environment);
            } else {
                throw new IllegalArgumentException("locationId or connectionKey is required");
            }
            try (java.sql.Connection c = conn) {
                try { metricsService.increment("external.testPhases", locationId != null ? "locationId=" + locationId : connectionKey); } catch (Exception ignore) {}
                java.util.List<String> out = metadataImporterService.findDistinctTestPhasesWithConnection(c, location, dataType, testerType);
                return ResponseEntity.ok(out);
            }
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(java.util.List.of());
        }
    }
}
