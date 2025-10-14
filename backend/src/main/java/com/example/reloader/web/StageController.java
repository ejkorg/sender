package com.example.reloader.web;

import com.example.reloader.service.RefDbService;
import com.example.reloader.stage.StageRecord;
import com.example.reloader.web.dto.StageRecordPage;
import com.example.reloader.web.dto.StageRecordView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

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
        List<StageRecord> records = refDbService.listRecords(site, senderId, status, offset, resolvedSize);
        long total = refDbService.countRecords(site, senderId, status);
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
                toIso(record.updatedAt())
        );
    }

    private String toIso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
