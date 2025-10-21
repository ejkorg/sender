package com.onsemi.cim.apps.exensio.exensioDearchiver.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExternalDbConfigCaseSensitivityTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesEnvironmentKeysIgnoringCase() throws Exception {
        String json = """
        {
          "EXTERNAL-QA": {
            "host": "jdbc:oracle:thin:@qa-host:1521/service",
            "user": "user",
            "password": "pw"
          }
        }
        """;
        ExternalDbConfig cfg = createConfig(json);
        try {
            Map<String, Object> resolved = cfg.getConfigForSite("EXTERNAL", "qa");
            assertNotNull(resolved, "Expected config for EXTERNAL qa environment");
            assertEquals("jdbc:oracle:thin:@qa-host:1521/service", resolved.get("host"));
        } finally {
            cfg.destroy();
        }
    }

    @Test
    void getConfigByKeyMatchesIgnoringCase() throws Exception {
        String json = """
        {
          "EXTERNAL-QA": {
            "host": "jdbc:oracle:thin:@qa-host:1521/service",
            "user": "user",
            "password": "pw"
          }
        }
        """;
        ExternalDbConfig cfg = createConfig(json);
        try {
            Map<String, Object> resolved = cfg.getConfigByKey("external-qa");
            assertNotNull(resolved, "Expected case-insensitive lookup by key");
            assertEquals("jdbc:oracle:thin:@qa-host:1521/service", resolved.get("host"));
        } finally {
            cfg.destroy();
        }
    }

    private ExternalDbConfig createConfig(String json) throws Exception {
        Path file = Files.createTempFile(tempDir, "dbconnections", ".json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        MockEnvironment env = new MockEnvironment();
        env.setProperty("RELOADER_DBCONN_PATH", file.toString());
        return new ExternalDbConfig(env);
    }
}
