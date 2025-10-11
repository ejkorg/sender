package com.example.reloader;

import com.example.reloader.stage.StageStatus;
import com.example.reloader.web.dto.ReloadFilterOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class ReloaderController {

    @Autowired
    private ReloaderService reloaderService;

    @GetMapping("/sites")
    public List<String> getSites() {
        return reloaderService.getSites();
    }

    @GetMapping("/stage/status")
    public List<StageStatus> getStageStatus() {
        return reloaderService.getStageStatuses();
    }

    @GetMapping("/stage/status/by")
    public List<StageStatus> getStageStatusFiltered(@RequestParam(required = false) String site,
                                                    @RequestParam(required = false) Integer senderId) {
        return reloaderService.getStageStatuses(site, senderId);
    }

    @PostMapping("/reload")
    public String reload(@RequestBody Map<String, String> params) {
        return reloaderService.processReload(params);
    }

    @GetMapping("/reload/filters")
    public ReloadFilterOptions reloadFilters(@RequestParam String site) {
        return reloaderService.getReloadFilters(site);
    }
}