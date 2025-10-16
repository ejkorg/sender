package com.onsemi.cim.apps.exensio.dearchiver.boot;

import com.onsemi.cim.apps.exensio.dearchiver.config.ExternalDbConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class DevExternalDbInitRunnerTest {

    @BeforeEach
    void clear() {
        // Avoid mutating global System properties in tests; rely on injected environments instead.
    }

    @AfterEach
    void tearDown() {
        // No-op
    }

    @Test
    void run_skips_when_flag_false() throws Exception {
    ExternalDbConfig cfg = mock(ExternalDbConfig.class);
    // Use a MockEnvironment and explicitly set the flag to false so this
    // test is deterministic even if the suite is run with -Dreloader.use-h2-external=true
    org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
    env.setProperty("reloader.use-h2-external", "false");
        DevExternalDbInitRunner runner = new DevExternalDbInitRunner(cfg, env);

        runner.run();

        verifyNoInteractions(cfg);
    }

    

    @TestPropertySource(properties = "reloader.use-h2-external=true")
    public static class TrueCase {
        @Test
        void run_executes_when_flag_true() throws Exception {
            ExternalDbConfig cfg = mock(ExternalDbConfig.class);
            org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
            // Make the true-case explicit and deterministic for the test
            env.setProperty("reloader.use-h2-external", "true");
            Connection conn = mock(Connection.class);
            Statement stmt = mock(Statement.class);

            when(cfg.getConnection("default", "qa")).thenReturn(conn);
            when(conn.createStatement()).thenReturn(stmt);

            DevExternalDbInitRunner runner = new DevExternalDbInitRunner(cfg, env);
            runner.run();

            verify(cfg).getConnection("default", "qa");
            verify(stmt).execute(anyString());
        }
    }

}
