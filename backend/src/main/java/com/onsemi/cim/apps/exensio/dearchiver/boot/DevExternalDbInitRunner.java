package com.onsemi.cim.apps.exensio.dearchiver.boot;

import com.onsemi.cim.apps.exensio.dearchiver.config.ExternalDbConfig;
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
    private final org.springframework.core.env.Environment env;

    public DevExternalDbInitRunner(ExternalDbConfig externalDbConfig, org.springframework.core.env.Environment env) {
        this.externalDbConfig = externalDbConfig;
        this.env = env;
    }

    @Override
    public void run(String... args) {
        // Guard: only run dev DDL when explicitly configured to use H2 as external DB
        boolean useH2 = com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getBooleanFlag(env, "reloader.use-h2-external", "RELOADER_USE_H2_EXTERNAL", false);
        if (!useH2) {
            log.info("DevExternalDbInitRunner: skipping external DDL because RELOADER_USE_H2_EXTERNAL is not true");
            return;
        }

        try (Connection c = externalDbConfig.getConnection("default", "qa"); Statement s = c.createStatement()) {
            log.info("DevExternalDbInitRunner: ensuring DTP_SENDER_QUEUE_ITEM exists in external DB (H2 mode)");
            s.execute("CREATE TABLE IF NOT EXISTS DTP_SENDER_QUEUE_ITEM (id BIGINT AUTO_INCREMENT PRIMARY KEY, id_metadata VARCHAR(255), id_data VARCHAR(255), id_sender INT, record_created TIMESTAMP)");
            log.info("DevExternalDbInitRunner: table created or already exists");
        } catch (Exception e) {
            log.warn("DevExternalDbInitRunner: failed to create external table: {}", e.getMessage());
        }
    }
}
