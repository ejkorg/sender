package com.example.reloader.boot;

import com.example.reloader.config.ExternalDbConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Dev-only runner that ensures a minimal DTP_SENDER_QUEUE_ITEM table exists in the external DB
 * so discovery pre-checks succeed in local/dev environments. Enabled only when 'dev' profile is active.
 */
@Component
@Profile("dev")
public class DevExternalDbInitRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DevExternalDbInitRunner.class);

    private final ExternalDbConfig externalDbConfig;

    public DevExternalDbInitRunner(ExternalDbConfig externalDbConfig) {
        this.externalDbConfig = externalDbConfig;
    }

    @Override
    public void run(String... args) {
        try (Connection c = externalDbConfig.getConnection("default", "qa"); Statement s = c.createStatement()) {
            log.info("DevExternalDbInitRunner: ensuring DTP_SENDER_QUEUE_ITEM exists in external DB");
            s.execute("CREATE TABLE IF NOT EXISTS DTP_SENDER_QUEUE_ITEM (id BIGINT AUTO_INCREMENT PRIMARY KEY, id_metadata VARCHAR(255), id_data VARCHAR(255), id_sender INT, record_created TIMESTAMP)");
            log.info("DevExternalDbInitRunner: table created or already exists");
        } catch (Exception e) {
            log.warn("DevExternalDbInitRunner: failed to create external table: {}", e.getMessage());
        }
    }
}
