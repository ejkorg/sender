package com.example.reloader;

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

    @PostMapping("/reload")
    public String reload(@RequestBody Map<String, String> params) {
        return reloaderService.processReload(params);
    }
}