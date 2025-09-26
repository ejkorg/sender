package com.example.reloader.config;

import com.example.reloader.service.ExternalLocationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class ExternalLocationDataLoader implements CommandLineRunner {

    private final ExternalLocationService service;

    @Value("${app.env.import-on-startup:false}")
    private boolean importOnStartup;

    public ExternalLocationDataLoader(ExternalLocationService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!importOnStartup) return;
        ClassPathResource r = new ClassPathResource("db/seed/external_locations.csv");
        try (InputStream is = r.getInputStream()) {
            service.importCsv(is);
        }
    }
}
