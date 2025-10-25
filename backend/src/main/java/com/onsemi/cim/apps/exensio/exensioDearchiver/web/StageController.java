package com.onsemi.cim.apps.exensio.exensioDearchiver.web;

import com.onsemi.cim.apps.exensio.exensioDearchiver.service.RefDbService;
import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageRecord;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.StageRecordPage;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.StageRecordView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/stage")
public class StageController {
    private final RefDbService refDbService;

    public StageController(RefDbService refDbService) {
        this.refDbService = refDbService;
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('USER')")
    @GetMapping("/records")
    public ResponseEntity<StageRecordPage> list(@RequestParam String site,
                                                @RequestParam(required = false) Integer senderId,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        if (site == null || site.isBlank()) {
            throw new IllegalArgumentException("site is required");
        }
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = size <= 0 ? 50 : Math.min(size, 500);
        int offset = resolvedPage * resolvedSize;
        // If the caller is not an admin, limit returned records to those owned by the user at the SQL level.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<StageRecord> records;
        long total;
        if (auth == null) {
            records = refDbService.listRecords(site, senderId, status, offset, resolvedSize);
            total = refDbService.countRecords(site, senderId, status);
        } else {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
            String username = auth.getName() == null ? "" : auth.getName().trim();
            if (isAdmin) {
                records = refDbService.listRecords(site, senderId, status, offset, resolvedSize);
                total = refDbService.countRecords(site, senderId, status);
            } else if (!username.isEmpty()) {
                records = refDbService.listRecordsForUser(site, senderId, status, offset, resolvedSize, username.toLowerCase());
                total = refDbService.countRecordsForUser(site, senderId, status, username.toLowerCase());
            } else {
                records = refDbService.listRecords(site, senderId, status, offset, resolvedSize);
                total = refDbService.countRecords(site, senderId, status);
            }
        }
        List<StageRecordView> items = records.stream().map(this::toView).toList();
        StageRecordPage response = new StageRecordPage(items, total, resolvedPage, resolvedSize);
        return ResponseEntity.ok(response);
    }

    private StageRecordView toView(StageRecord record) {
        return new StageRecordView(
                record.id(),
                record.site(),
                record.senderId(),
                record.metadataId(),
                record.dataId(),
                record.status(),
                record.errorMessage(),
                toIso(record.createdAt()),
                toIso(record.updatedAt()),
                toIso(record.processedAt()),
                displayUser(record.stagedBy()),
                displayUser(record.lastRequestedBy()),
                toIso(record.lastRequestedAt())
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
}
