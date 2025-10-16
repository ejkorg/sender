package com.onsemi.cim.apps.exensio.dearchiver.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.core.instrument.MeterRegistry;

import java.io.IOException;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

public class ExternalDbConfigHikariParsingTest {

    // Minimal ObjectProvider stub returning null for MeterRegistry
    private static final ObjectProvider<MeterRegistry> NULL_MR = new ObjectProvider<>() {
        @Override public MeterRegistry getObject(Object... args) { throw new UnsupportedOperationException(); }
        @Override public MeterRegistry getObject() { throw new UnsupportedOperationException(); }
        @Override public MeterRegistry getIfAvailable() { return null; }
        @Override public MeterRegistry getIfUnique() { return null; }
    };

    @Test
    void hikariOverrides_asJsonString_areApplied() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("RELOADER_DBCONN_PATH", new ClassPathResource("dbconnections.test.json").getFile().getAbsolutePath());
        env.setProperty("external-db.hikari.maximum-pool-size", "10");

        ExternalDbConfig cfg = new ExternalDbConfig(env, NULL_MR);
        // Force creation of pool for EXAMPLE_SITE
        try (Connection c = cfg.getConnectionByKey("EXAMPLE_SITE", null)) {
            assertNotNull(c);
        }
        // Resolved key equals site (no env)
        int max = cfg.getConfiguredMaxPoolSizeForKey("EXAMPLE_SITE");
        assertEquals(7, max, "maximumPoolSize should be overridden to 7 from JSON string form");
    }

    @Test
    void hikariOverrides_asMap_areApplied() throws Exception {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("RELOADER_DBCONN_PATH", new ClassPathResource("dbconnections.test.json").getFile().getAbsolutePath());
        env.setProperty("external-db.hikari.maximum-pool-size", "10");

        ExternalDbConfig cfg = new ExternalDbConfig(env, NULL_MR);
        try (Connection c = cfg.getConnectionByKey("EXAMPLE_SITE_MAP", null)) {
            assertNotNull(c);
        }
        int max = cfg.getConfiguredMaxPoolSizeForKey("EXAMPLE_SITE_MAP");
        assertEquals(13, max, "maximumPoolSize should be overridden to 13 from Map form");
    }
}
