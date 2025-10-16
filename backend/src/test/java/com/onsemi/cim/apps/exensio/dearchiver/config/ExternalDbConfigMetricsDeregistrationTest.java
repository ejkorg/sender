package com.onsemi.cim.apps.exensio.dearchiver.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

public class ExternalDbConfigMetricsDeregistrationTest {

    @Test
    public void metricsAreRegisteredAndRemovedOnRecreate() throws Exception {
        MockEnvironment env = new MockEnvironment();
        // point to test fixture dbconnections that uses H2 URLs (exists in test resources)
        String path = this.getClass().getClassLoader().getResource("dbconnections.test.json").getFile();
        env.setProperty("RELOADER_DBCONN_PATH", path);
        // small cache so eviction/management is straightforward
        env.setProperty("external-db.cache.max-pools", "2");

        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        ObjectProvider<MeterRegistry> provider = new ObjectProvider<>() {
            @Override
            public MeterRegistry getObject(Object... args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public MeterRegistry getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public MeterRegistry getIfAvailable() {
                return registry;
            }

            @Override
            public MeterRegistry getIfUnique() {
                return registry;
            }
        };

        ExternalDbConfig cfg = new ExternalDbConfig(env, provider);

        String resolvedKey = "EXAMPLE_SITE";

        // create a connection which triggers pool creation and gauge registration
        try (Connection c = cfg.getConnectionByKey(resolvedKey, null)) {
            assertNotNull(c);
        }

        // Expect a gauge with tag pool=EXAMPLE_SITE to exist
        boolean found = registry.getMeters().stream()
                .anyMatch(m -> {
                    String poolTag = m.getId().getTag("pool");
                    return resolvedKey.equals(poolTag);
                });
        assertTrue(found, "Expected meter with pool tag to be registered");

        // Recreate pool which should remove meters for that pool
        cfg.recreatePool(resolvedKey);

        boolean foundAfter = registry.getMeters().stream()
                .anyMatch(m -> {
                    String poolTag = m.getId().getTag("pool");
                    return resolvedKey.equals(poolTag);
                });
        assertFalse(foundAfter, "Expected meters for the pool to be removed after recreatePool");
    }
}
