package com.onsemi.cim.apps.exensio.exensioDearchiver.web;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.SenderQueueEntry;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.SenderQueueRepository;
import com.onsemi.cim.apps.exensio.exensioDearchiver.service.RefDbService;
import com.onsemi.cim.apps.exensio.exensioDearchiver.service.SenderDispatchService;
import com.onsemi.cim.apps.exensio.exensioDearchiver.service.SenderService;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.DuplicatePayload;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.PayloadCandidate;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageResult;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.DiscoveryPreviewRequest;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.DiscoveryPreviewResponse;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.StagePayloadRequest;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.StagePayloadResponse;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.DispatchRequest;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.DispatchResponse;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.DuplicatePayloadView;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.EnqueueRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.time.Instant;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/senders")
public class SenderController {
    private static final Logger log = LoggerFactory.getLogger(SenderController.class);
    private final SenderService senderService;
    private final SenderQueueRepository repo;
    private final com.onsemi.cim.apps.exensio.exensioDearchiver.service.MetadataImporterService metadataImporterService;
    private final com.onsemi.cim.apps.exensio.exensioDearchiver.service.MetricsService metricsService;
    private final RefDbService refDbService;
    private final SenderDispatchService senderDispatchService;
    public SenderController(SenderService senderService, SenderQueueRepository repo, com.onsemi.cim.apps.exensio.exensioDearchiver.service.MetadataImporterService metadataImporterService, com.onsemi.cim.apps.exensio.exensioDearchiver.service.MetricsService metricsService, RefDbService refDbService, SenderDispatchService senderDispatchService) {
        this.senderService = senderService;
        this.repo = repo;
        this.metadataImporterService = metadataImporterService;
        this.metricsService = metricsService;
        this.refDbService = refDbService;
        this.senderDispatchService = senderDispatchService;
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
    public ResponseEntity<com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.EnqueueResult> enqueue(@PathVariable("id") Integer id, @RequestBody EnqueueRequest req) {
        Integer senderId = id;
        if (req.getSenderId() != null) senderId = req.getSenderId();
        SenderService.EnqueueResultHolder holder = senderService.enqueuePayloadsWithResult(senderId, req.getPayloadIds(), req.getSource() != null ? req.getSource() : "ui_submit");
        com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.EnqueueResult result = new com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.EnqueueResult(holder.enqueuedCount, holder.skippedPayloads);
        return ResponseEntity.ok(result);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('USER','ADMIN')")
    @PostMapping("/{id}/dispatch")
    public ResponseEntity<DispatchResponse> dispatch(@PathVariable("id") Integer id, @RequestBody DispatchRequest request) {
        if (request == null || request.site() == null || request.site().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Integer resolvedSender = request.senderId() != null ? request.senderId() : id;
        if (resolvedSender == null || resolvedSender <= 0) {
            return ResponseEntity.badRequest().build();
        }
        int dispatched = senderDispatchService.dispatchSender(request.site(), resolvedSender, request.limit());
        return ResponseEntity.ok(new DispatchResponse(request.site(), resolvedSender, dispatched));
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
            com.onsemi.cim.apps.exensio.exensioDearchiver.entity.ExternalLocation loc = metadataImporterService.findLocationById(locationId);
            if (loc != null && loc.getSite() != null && !loc.getSite().isBlank()) {
                site = loc.getSite();
            }
        }

    int added = metadataImporterService.discoverAndEnqueue(site, environment, id, startDate, endDate, testerType, dataType, testPhase, filterLocation, locationId, writeListFile, numberOfDataToSend, countLimitTrigger);
    return ResponseEntity.ok("Discovered and staged " + added + " payloads");
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('USER')")
    @PostMapping("/{id}/discover/preview")
    public ResponseEntity<DiscoveryPreviewResponse> preview(@PathVariable("id") Integer id,
                                                            @RequestBody DiscoveryPreviewRequest request) {
    if (log.isInfoEnabled()) {
        log.info("Preview request for sender={} site={} location={} dataType={} testerType={} testPhase={} start={} end={} page={} size={}"
            , id, request.site(), request.location(), request.dataType(), request.testerType(), request.testPhase(), request.startDate(), request.endDate(), request.page(), request.size());
    }
        DiscoveryPreviewResponse response = metadataImporterService.previewMetadata(
                request.site(),
                request.environment(),
                id,
                request.startDate(),
                request.endDate(),
                request.testerType(),
                request.dataType(),
                request.testPhase(),
                request.location(),
                request.page(),
                request.size());
        if (log.isInfoEnabled()) {
            log.info("Preview response rows={} total={}", response.items().size(), response.total());
        }
        return ResponseEntity.ok(response);
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('USER')")
    @PostMapping("/{id}/stage")
    public ResponseEntity<StagePayloadResponse> stagePayloads(@PathVariable("id") Integer id,
                                                              @RequestBody StagePayloadRequest request) {
        String site = request.site();
        if (site == null || site.isBlank()) {
            throw new IllegalArgumentException("site is required");
        }
        Integer resolvedSender = request.senderId() != null ? request.senderId() : id;
        if (resolvedSender == null || resolvedSender <= 0) {
            throw new IllegalArgumentException("senderId is required");
        }
        List<StagePayloadRequest.Payload> payloads = request.payloads();
        if (payloads == null || payloads.isEmpty()) {
            return ResponseEntity.ok(new StagePayloadResponse(0, 0, List.<DuplicatePayloadView>of(), 0, false));
        }
        List<PayloadCandidate> candidates = payloads.stream()
                .map(p -> new PayloadCandidate(p.metadataId(), p.dataId()))
                .collect(Collectors.toList());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String requestedBy = authentication != null && authentication.getName() != null ? authentication.getName().trim() : "ui";
        if (requestedBy.isEmpty()) {
            requestedBy = "ui";
        }
        StageResult result = refDbService.stagePayloads(site, resolvedSender, requestedBy, candidates, request.forceDuplicates());
        boolean requiresConfirmation = result.duplicates().stream().anyMatch(DuplicatePayload::requiresConfirmation);
        int dispatched = 0;
        if (request.triggerDispatch() && !requiresConfirmation) {
            dispatched = senderDispatchService.dispatchSender(site, resolvedSender);
        } else if (request.triggerDispatch() && requiresConfirmation) {
            log.info("Dispatch deferred for site {} sender {} pending duplicate confirmation", site, resolvedSender);
        }
        List<DuplicatePayloadView> duplicateViews = result.duplicates().stream().map(this::toDuplicateView).toList();
        StagePayloadResponse response = new StagePayloadResponse(result.stagedCount(), duplicateViews.size(), duplicateViews, dispatched, requiresConfirmation);
        return ResponseEntity.ok(response);
    }

    private DuplicatePayloadView toDuplicateView(DuplicatePayload payload) {
        return new DuplicatePayloadView(
                payload.metadataId(),
                payload.dataId(),
                payload.previousStatus(),
                toIso(payload.previousProcessedAt()),
                displayUser(payload.stagedBy()),
                toIso(payload.stagedAt()),
                displayUser(payload.lastRequestedBy()),
                toIso(payload.lastRequestedAt()),
                payload.requiresConfirmation()
        );
    }

    private String toIso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private String displayUser(String value) {
        if (value == null) {
            return "unknown";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "unknown" : trimmed;
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
                                                                                       @RequestParam(required = false, name = "senderId") Integer senderId,
                                                                                       @RequestParam(required = false, name = "senderName") String senderName,
                                                                                       // saved ExternalLocation id used to select which DB connection to use
                                                                                       @RequestParam(required = false, name = "locationId") Long locationId,
                                                                                       // alternatively, allow callers to provide the connection key directly
                                                                                       @RequestParam(required = false, name = "connectionKey") String connectionKey) {
        try {
            com.onsemi.cim.apps.exensio.exensioDearchiver.entity.ExternalLocation loc = null;
            java.sql.Connection conn = null;
            if (locationId != null) {
                loc = metadataImporterService.findLocationById(locationId);
                if (loc == null) throw new IllegalArgumentException("locationId not found");
                conn = metadataImporterService.resolveConnectionForLocation(loc, environment);
            } else if (connectionKey != null && !connectionKey.isBlank()) {
                if (metadataLocation == null || metadataLocation.isBlank()) {
                    throw new IllegalArgumentException("metadataLocation is required when using a connection key");
                }
                conn = metadataImporterService.resolveConnectionForKey(connectionKey, environment);
            } else {
                throw new IllegalArgumentException("locationId or connectionKey is required");
            }

            try (java.sql.Connection c = conn) {
                // metric: record lookup by saved connection key or locationId
                String key = loc != null ? ("locationId=" + loc.getId()) : connectionKey;
                try { metricsService.increment("external.lookup", key); } catch (Exception ignore) {}
                java.util.List<com.onsemi.cim.apps.exensio.exensioDearchiver.repository.SenderCandidate> res = metadataImporterService.findSendersWithConnection(c, metadataLocation, dataType, testerType, testPhase);
                    java.util.List<java.util.Map<String,Object>> out = new java.util.ArrayList<>();
                    for (com.onsemi.cim.apps.exensio.exensioDearchiver.repository.SenderCandidate s : res) {
                        if (senderId != null && s.getIdSender() != null && !java.util.Objects.equals(senderId, s.getIdSender())) {
                            continue;
                        }
                        if (senderName != null && !senderName.isBlank()) {
                            String candidateName = s.getName() == null ? "" : s.getName();
                            if (!candidateName.equalsIgnoreCase(senderName.trim())) {
                                continue;
                            }
                        }
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
                com.onsemi.cim.apps.exensio.exensioDearchiver.entity.ExternalLocation loc = metadataImporterService.findLocationById(locationId);
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
                com.onsemi.cim.apps.exensio.exensioDearchiver.entity.ExternalLocation loc = metadataImporterService.findLocationById(locationId);
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
                com.onsemi.cim.apps.exensio.exensioDearchiver.entity.ExternalLocation loc = metadataImporterService.findLocationById(locationId);
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
                                                                             @RequestParam(required = false, name = "senderId") Integer senderId,
                                                                             @RequestParam(required = false, name = "senderName") String senderName,
                                                                             @RequestParam(defaultValue = "qa") String environment) {
        try {
            java.sql.Connection conn = null;
            if (locationId != null) {
                com.onsemi.cim.apps.exensio.exensioDearchiver.entity.ExternalLocation loc = metadataImporterService.findLocationById(locationId);
                if (loc == null) throw new IllegalArgumentException("locationId not found");
                conn = metadataImporterService.resolveConnectionForLocation(loc, environment);
            } else if (connectionKey != null && !connectionKey.isBlank()) {
                conn = metadataImporterService.resolveConnectionForKey(connectionKey, environment);
            } else {
                throw new IllegalArgumentException("locationId or connectionKey is required");
            }
            if (location == null || location.isBlank() || dataType == null || dataType.isBlank() || testerType == null || testerType.isBlank()) {
                return ResponseEntity.ok(java.util.List.of());
            }
            try (java.sql.Connection c = conn) {
                try { metricsService.increment("external.testPhases", locationId != null ? "locationId=" + locationId : connectionKey); } catch (Exception ignore) {}
                java.util.List<String> out = metadataImporterService.findDistinctTestPhasesWithConnection(c, location, dataType, testerType, senderId, senderName);
                return ResponseEntity.ok(out);
            }
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(java.util.List.of());
        }
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('USER','ADMIN')")
    @GetMapping("/external/senders")
    public ResponseEntity<java.util.List<java.util.Map<String,Object>>> externalSenders(@RequestParam(required = false, name = "locationId") Long locationId,
                                                                                         @RequestParam(required = false, name = "connectionKey") String connectionKey,
                                                                                         @RequestParam(defaultValue = "qa") String environment) {
        try {
            java.sql.Connection conn = null;
            if (locationId != null) {
                com.onsemi.cim.apps.exensio.exensioDearchiver.entity.ExternalLocation loc = metadataImporterService.findLocationById(locationId);
                if (loc == null) throw new IllegalArgumentException("locationId not found");
                conn = metadataImporterService.resolveConnectionForLocation(loc, environment);
            } else if (connectionKey != null && !connectionKey.isBlank()) {
                conn = metadataImporterService.resolveConnectionForKey(connectionKey, environment);
            } else {
                throw new IllegalArgumentException("locationId or connectionKey is required");
            }
            try (java.sql.Connection c = conn) {
                try { metricsService.increment("external.senders", locationId != null ? "locationId=" + locationId : connectionKey); } catch (Exception ignore) {}
                java.util.List<com.onsemi.cim.apps.exensio.exensioDearchiver.repository.SenderCandidate> senders = metadataImporterService.findAllSendersWithConnection(c);
                java.util.List<java.util.Map<String,Object>> out = new java.util.ArrayList<>();
                for (com.onsemi.cim.apps.exensio.exensioDearchiver.repository.SenderCandidate s : senders) {
                    java.util.Map<String,Object> m = new java.util.HashMap<>();
                    m.put("idSender", s.getIdSender());
                    m.put("name", s.getName());
                    m.put("id", s.getIdSender());
                    out.add(m);
                }
                return ResponseEntity.ok(out);
            }
        } catch (Exception ex) {
            return ResponseEntity.status(500).body(java.util.List.of());
        }
    }
}
