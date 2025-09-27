package com.example.reloader.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = ExternalDbConfigMicrometerIntegrationTest.TestConfig.class)
@ActiveProfiles("test")
public class ExternalDbConfigMicrometerIntegrationTest {

    @Configuration
    static class TestConfig {
        @Bean
        public SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        public ExternalDbConfig externalDbConfig(org.springframework.core.env.Environment env, ObjectProvider<io.micrometer.core.instrument.MeterRegistry> provider) throws java.io.IOException {
            // Ensure ExternalDbConfig loads the test dbconnections fixture
            String path = this.getClass().getClassLoader().getResource("dbconnections.test.json").getFile();
            System.setProperty("RELOADER_DBCONN_PATH", path);
            return new ExternalDbConfig(env, provider);
        }
    }

    @Autowired
    private ExternalDbConfig externalDbConfig;

    @Autowired
    private SimpleMeterRegistry meterRegistry;

    @Test
    public void hikariMetricsTrackerFactoryRegistersMeters() throws Exception {
        // Ensure a pool is created
        try (Connection c = externalDbConfig.getConnectionByKey("EXAMPLE_SITE", null)) {
            // no-op
        }

        // Expect at least one meter whose id name contains 'hikaricp' or has a poolName tag starting with 'external-'
        boolean found = false;
        for (Meter m : meterRegistry.getMeters()) {
            String name = m.getId().getName();
            String poolTag = m.getId().getTag("poolName");
            if ((name != null && name.toLowerCase().contains("hikaricp")) || (poolTag != null && poolTag.startsWith("external-"))) {
                found = true;
                break;
            }
        }

        assertTrue(found, "Expected Micrometer/Hikari meters to be registered for the created pool");
    }
}
