package com.onsemi.cim.apps.exensio.exensioDearchiver;

import com.onsemi.cim.apps.exensio.exensioDearchiver.stage.StageStatus;
import com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto.ReloadFilterOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class ExensioDearchiveController {

    @Autowired
    private ExensioDearchiveService exensioDearchiveService;

    @GetMapping("/sites")
    public List<String> getSites() {
        return exensioDearchiveService.getSites();
    }

    @GetMapping("/stage/status")
    public List<StageStatus> getStageStatus() {
        return exensioDearchiveService.getStageStatuses();
    }

    @GetMapping("/stage/status/by")
    public List<StageStatus> getStageStatusFiltered(@RequestParam(required = false) String site,
                                                    @RequestParam(required = false) Integer senderId) {
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
