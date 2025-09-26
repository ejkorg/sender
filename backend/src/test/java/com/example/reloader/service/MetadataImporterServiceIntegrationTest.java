package com.example.reloader.service;

import com.example.reloader.config.ExternalDbConfig;
import com.example.reloader.config.DiscoveryProperties;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "reloader.jwt.secret=0123456789abcdef0123456789abcdef")
public class MetadataImporterServiceIntegrationTest {

    @Autowired
    private MetadataImporterService importerService;

    @Autowired
    private DiscoveryProperties discoveryProperties;

    @MockBean
    private SenderService senderService;

    @MockBean
    private MailService mailService;

    @MockBean
    private ExternalDbConfig externalDbConfig; // we'll override getConnection

    private Connection extConn;

    @BeforeEach
    public void setup() throws Exception {
        extConn = DriverManager.getConnection("jdbc:h2:mem:external;DB_CLOSE_DELAY=-1");
        try (Statement s = extConn.createStatement()) {
            // Create a simple view table and seed data
            s.execute("CREATE TABLE all_metadata_view (lot VARCHAR(50), id VARCHAR(50), id_data VARCHAR(50), end_time TIMESTAMP, tester_type VARCHAR(50), data_type VARCHAR(50));");
            s.execute("CREATE TABLE DTP_SENDER_QUEUE_ITEM (id VARCHAR(50), id_sender VARCHAR(50));");
            s.execute("INSERT INTO all_metadata_view (lot, id, id_data, end_time, tester_type, data_type) VALUES ('L1','ID1','DATA1', '2025-01-15 10:00:00','T1','D1');");
            s.execute("INSERT INTO all_metadata_view (lot, id, id_data, end_time, tester_type, data_type) VALUES ('L2','ID2','DATA2', '2025-01-16 11:00:00','T1','D1');");
            // seed queue count small
            s.execute("INSERT INTO DTP_SENDER_QUEUE_ITEM (id, id_sender) VALUES ('Q1','42');");
        }

    // Make ExternalDbConfig return a fresh H2 connection for site "TEST_SITE" and environment "qa"
    when(externalDbConfig.getConnection("TEST_SITE", "qa")).thenAnswer(inv -> DriverManager.getConnection("jdbc:h2:mem:external;DB_CLOSE_DELAY=-1"));

        // Default discovery properties
        discoveryProperties.setNotifyRecipient(null);
        discoveryProperties.setNotifyAttachList(false);
    }

    @AfterEach
    public void teardown() throws Exception {
        if (extConn != null && !extConn.isClosed()) extConn.close();
    }

    @Test
    public void testDiscoverAndEnqueue() throws Exception {
        // Mock senderService.enqueuePayloadsWithResult to return EnqueueResultHolder matching the passed list size
        when(senderService.enqueuePayloadsWithResult(anyInt(), any(List.class), any(String.class))).thenAnswer(inv -> {
            List<?> list = inv.getArgument(1);
            return new SenderService.EnqueueResultHolder(list.size(), java.util.Collections.emptyList());
        });

    int added = importerService.discoverAndEnqueue("TEST_SITE", "qa", 42, "2025-01-01 00:00:00.000000", "2025-12-31 23:59:59.999999", "T1", "D1", null, null, null, false, 0, 1000);

        assertThat(added).isEqualTo(2);

        // Verify senderService.enqueuePayloadsWithResult was called (fallback removed in tests)
        verify(senderService).enqueuePayloadsWithResult(42, List.of("ID1,DATA1", "ID2,DATA2"), "metadata_discover");
    }
}
