package com.example.reloader.boot;

import com.example.reloader.config.ExternalDbConfig;
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
        // Use a real StandardEnvironment and ensure no system property is set so
        // the flag resolves to false in a deterministic way (avoids mocking
        // overloaded getProperty signatures).
        Environment env = new org.springframework.core.env.StandardEnvironment();
        DevExternalDbInitRunner runner = new DevExternalDbInitRunner(cfg, env);

        runner.run();

        verifyNoInteractions(cfg);
    }

    

    @TestPropertySource(properties = "reloader.use-h2-external=true")
    public static class TrueCase {
        @Test
        void run_executes_when_flag_true() throws Exception {
            ExternalDbConfig cfg = mock(ExternalDbConfig.class);
            Environment env = new org.springframework.core.env.StandardEnvironment();
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
