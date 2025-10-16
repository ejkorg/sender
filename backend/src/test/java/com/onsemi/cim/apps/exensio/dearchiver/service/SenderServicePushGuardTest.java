package com.onsemi.cim.apps.exensio.dearchiver.service;

import com.onsemi.cim.apps.exensio.dearchiver.entity.SenderQueueEntry;
import com.onsemi.cim.apps.exensio.dearchiver.repository.SenderQueueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.junit.jupiter.api.Nested;
import static org.mockito.Mockito.when;
import java.sql.DriverManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
public class SenderServicePushGuardTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    public static class WhenWritesDisabled {

        @Autowired
        private SenderService senderService;

        @MockBean
        private com.onsemi.cim.apps.exensio.dearchiver.config.ExternalDbConfig externalDbConfig;

        @Test
        public void pushToExternal_throwsWhenWritesDisabled() {
            // Default test context does not enable external writes
            java.util.List<SenderQueueEntry> items = new ArrayList<>();
            items.add(new SenderQueueEntry(1, "p1", "test"));

            assertThrows(IllegalStateException.class, () -> {
                senderService.pushToExternalQueue("EXAMPLE_SITE", 1, items);
            });
        }
    }

    @Nested
    @SpringBootTest(properties = "external-db.allow-writes=true")
    @ActiveProfiles("test")
    public static class WhenWritesEnabled {

        @Autowired
        private SenderService senderService;

        @Autowired
        private SenderQueueRepository senderQueueRepository;

        @MockBean
        private com.onsemi.cim.apps.exensio.dearchiver.config.ExternalDbConfig externalDbConfig;

        @Test
        public void pushToExternal_succeedsWhenWritesEnabled() throws Exception {
            java.util.List<SenderQueueEntry> items = new ArrayList<>();
            items.add(new SenderQueueEntry(1, "p2", "test"));

            // Stub ExternalDbConfig to return an in-memory H2 connection so the test doesn't try to contact Oracle
            when(externalDbConfig.getConnection("EXAMPLE_SITE")).thenReturn(DriverManager.getConnection("jdbc:h2:mem:external_repo;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:external_h2_seed.sql'", "sa", ""));

            // This should no longer attempt to contact Oracle; assert no IllegalStateException is thrown.
            senderService.pushToExternalQueue("EXAMPLE_SITE", 1, items);

            // locally, ensure the repository still exists / queryable
            long count = senderQueueRepository.countByStatus("NEW");
            assertThat(count).isGreaterThanOrEqualTo(0);
        }
    }

}
