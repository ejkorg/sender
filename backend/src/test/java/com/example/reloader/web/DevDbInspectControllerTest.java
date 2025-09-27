package com.example.reloader.web;

import com.example.reloader.config.ExternalDbConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public class DevDbInspectControllerTest {

    @BeforeEach
    void clear() {
        // Avoid mutating global System properties in tests; rely on injected environments instead.
    }

    @AfterEach
    void tearDown() {
        // No-op
    }

    @Test
    void createExternalQueueTable_skips_when_flag_false() {
        ExternalDbConfig cfg = mock(ExternalDbConfig.class);
        Environment env = new org.springframework.core.env.StandardEnvironment();
        DevDbInspectController c = new DevDbInspectController(cfg, env);

        Map<String, Object> out = c.createExternalQueueTable("default", "qa");
        assertEquals(false, out.get("created"));
        assertEquals("Dev external DDL is disabled. Set RELOADER_USE_H2_EXTERNAL=true to enable", out.get("error"));
    }

    

    @TestPropertySource(properties = "reloader.use-h2-external=true")
    public static class TrueCase {
        @Test
        void createExternalQueueTable_runs_with_property_true() throws Exception {
            ExternalDbConfig cfg = mock(ExternalDbConfig.class);
            Environment env = new org.springframework.core.env.StandardEnvironment();
            Connection conn = mock(Connection.class);
            Statement stmt = mock(Statement.class);

            when(cfg.getConnection("default", "qa")).thenReturn(conn);
            when(conn.createStatement()).thenReturn(stmt);

            DevDbInspectController c = new DevDbInspectController(cfg, env);
            Map<String, Object> out = c.createExternalQueueTable("default", "qa");

            assertEquals(true, out.get("created"));
            verify(stmt).execute(org.mockito.ArgumentMatchers.anyString());
        }
    }

}
