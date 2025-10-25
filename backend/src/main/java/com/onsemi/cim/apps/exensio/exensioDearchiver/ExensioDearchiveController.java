package com.onsemi.cim.apps.exensio.exensioDearchiver;

import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageStatus;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.ReloadFilterOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.stream.Collectors;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class ExensioDearchiveController {

    @Autowired
    private ExensioDearchiveService exensioDearchiveService;

    @GetMapping("/sites")
    public List<String> getSites() {
        return exensioDearchiveService.getSites();
    }

    @GetMapping("/stage/status")
    public List<StageStatus> getStageStatus() {
        // If the caller is not an admin, return SQL-filtered results scoped to that user for performance
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return exensioDearchiveService.getStageStatuses();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
        String username = auth.getName() == null ? "" : auth.getName().trim();
        if (isAdmin) {
            return exensioDearchiveService.getStageStatuses();
        } else if (!username.isEmpty()) {
            return exensioDearchiveService.getStageStatusesForUser(null, null, username.toLowerCase());
        }
        return exensioDearchiveService.getStageStatuses();
    }

    @GetMapping("/stage/status/by")
    public List<StageStatus> getStageStatusFiltered(@RequestParam(required = false) String site,
                                                    @RequestParam(required = false) Integer senderId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return exensioDearchiveService.getStageStatuses(site, senderId);
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ADMIN"));
        String username = auth.getName() == null ? "" : auth.getName().trim();
        if (isAdmin) {
            return exensioDearchiveService.getStageStatuses(site, senderId);
        } else if (!username.isEmpty()) {
            return exensioDearchiveService.getStageStatusesForUser(site, senderId, username.toLowerCase());
        }
        return exensioDearchiveService.getStageStatuses(site, senderId);
    }

    @PostMapping("/reload")
    public String reload(@RequestBody Map<String, String> params) {
        return exensioDearchiveService.processReload(params);
    }

    @GetMapping("/reload/filters")
    public ReloadFilterOptions reloadFilters(@RequestParam String site) {
        return exensioDearchiveService.getReloadFilters(site);
    }
}
