package com.example.reloader.repository;

import com.example.reloader.config.ExternalDbConfig;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "reloader.jwt.secret=0123456789abcdef0123456789abcdef")
public class JdbcExternalMetadataRepositoryIntegrationTest {

    @Autowired
    private JdbcExternalMetadataRepository repository;

    @MockBean
    private ExternalDbConfig externalDbConfig;

    private Connection extConn;

    @BeforeEach
    public void setup() throws Exception {
        // create a dedicated H2 database for this test and seed it
        extConn = DriverManager.getConnection("jdbc:h2:mem:external_repo;DB_CLOSE_DELAY=-1");
        try (Statement s = extConn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS all_metadata_view (lot VARCHAR(50), id VARCHAR(50), id_data VARCHAR(50), end_time TIMESTAMP, tester_type VARCHAR(50), data_type VARCHAR(50), test_phase VARCHAR(50), location VARCHAR(50));");
            // ensure clean state for test runs
            s.execute("DELETE FROM all_metadata_view;");
            s.execute("INSERT INTO all_metadata_view (lot, id, id_data, end_time, tester_type, data_type, test_phase, location) VALUES ('L1','ID1','DATA1', '2025-01-15 10:00:00','T1','D1','PH1','LOC1');");
            s.execute("INSERT INTO all_metadata_view (lot, id, id_data, end_time, tester_type, data_type, test_phase, location) VALUES ('L2','ID2','DATA2', '2025-01-16 11:00:00','T1','D1','PH1','LOC1');");
            s.execute("INSERT INTO all_metadata_view (lot, id, id_data, end_time, tester_type, data_type, test_phase, location) VALUES ('L3','ID3','DATA3', '2025-01-17 12:00:00','T2','D2','PH2','LOC2');");
        }

        // make ExternalDbConfig return fresh connections to the same in-memory DB
        when(externalDbConfig.getConnection("TEST_SITE", "qa")).thenAnswer(inv -> DriverManager.getConnection("jdbc:h2:mem:external_repo;DB_CLOSE_DELAY=-1"));
    }

    @AfterEach
    public void teardown() throws Exception {
        if (extConn != null && !extConn.isClosed()) extConn.close();
    }

    @Test
    public void testFindMetadataReturnsAllMatchingRows() {
        List<MetadataRow> rows = repository.findMetadata("TEST_SITE", "qa", LocalDateTime.of(2025,1,1,0,0), LocalDateTime.of(2025,12,31,23,59), "D1", "PH1", "T1", "LOC1", 100);
        assertThat(rows).hasSize(2);
        List<String> ids = new ArrayList<>();
        for (MetadataRow r : rows) ids.add(r.getId());
        assertThat(ids).containsExactlyInAnyOrder("ID1", "ID2");
    }

    @Test
    public void testStreamMetadataRespectsLimit() {
        List<MetadataRow> collected = new ArrayList<>();
        repository.streamMetadata("TEST_SITE", "qa", LocalDateTime.of(2025,1,1,0,0), LocalDateTime.of(2025,12,31,23,59), null, null, null, null, 2, collected::add);
        assertThat(collected).hasSize(2);
    }

    @Test
    public void testFindSendersMatchesNullTestPhaseWhenBlankOrNone() throws Exception {
        // create minimal dtp_* tables used by findSendersWithConnection
        try (Statement s = extConn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS dtp_dist_conf (id INT PRIMARY KEY AUTO_INCREMENT, id_location INT, id_data_type INT, id_tester_type INT, id_data_type_ext INT, id_sender INT);");
            s.execute("CREATE TABLE IF NOT EXISTS dtp_location (id INT PRIMARY KEY AUTO_INCREMENT, location VARCHAR(100));");
            s.execute("CREATE TABLE IF NOT EXISTS dtp_data_type (id INT PRIMARY KEY AUTO_INCREMENT, data_type VARCHAR(100));");
            s.execute("CREATE TABLE IF NOT EXISTS dtp_tester_type (id INT PRIMARY KEY AUTO_INCREMENT, type VARCHAR(100));");
            s.execute("CREATE TABLE IF NOT EXISTS dtp_data_type_ext (id INT PRIMARY KEY AUTO_INCREMENT, data_type_ext VARCHAR(100));");
            s.execute("CREATE TABLE IF NOT EXISTS dtp_sender (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(200));");
            // clean
            s.execute("DELETE FROM dtp_dist_conf;");
            s.execute("DELETE FROM dtp_location;");
            s.execute("DELETE FROM dtp_data_type;");
            s.execute("DELETE FROM dtp_tester_type;");
            s.execute("DELETE FROM dtp_data_type_ext;");
            s.execute("DELETE FROM dtp_sender;");

            // seed supporting tables
            s.execute("INSERT INTO dtp_location (id, location) VALUES (1,'LOC1'), (2,'LOC2');");
            s.execute("INSERT INTO dtp_data_type (id, data_type) VALUES (1,'D1'), (2,'D2');");
            s.execute("INSERT INTO dtp_tester_type (id, type) VALUES (1,'T1'), (2,'T2');");
            s.execute("INSERT INTO dtp_data_type_ext (id, data_type_ext) VALUES (1,'PH1');");
            s.execute("INSERT INTO dtp_sender (id, name) VALUES (10,'SENDER_A'), (20,'SENDER_B');");

            // insert distribution configs: one with data_type_ext = 1 (PH1) and one with NULL (no test phase)
            s.execute("INSERT INTO dtp_dist_conf (id, id_location, id_data_type, id_tester_type, id_data_type_ext, id_sender) VALUES (1,1,1,1,1,10);");
            s.execute("INSERT INTO dtp_dist_conf (id, id_location, id_data_type, id_tester_type, id_data_type_ext, id_sender) VALUES (2,1,1,1,NULL,20);");
        }

        // Use repository.findSendersWithConnection to query
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:external_repo;DB_CLOSE_DELAY=-1")) {
            // when testPhase = 'PH1' we should get sender 10
            java.util.List<SenderCandidate> res1 = repository.findSendersWithConnection(c, "LOC1", "D1", "T1", "PH1");
            assertThat(res1).extracting(SenderCandidate::getIdSender).contains(10);

            // when testPhase is blank string, expect to match the NULL entry (sender 20)
            java.util.List<SenderCandidate> res2 = repository.findSendersWithConnection(c, "LOC1", "D1", "T1", "");
            assertThat(res2).extracting(SenderCandidate::getIdSender).contains(20);

            // when testPhase is 'NONE' explicitly, also expect sender 20
            java.util.List<SenderCandidate> res3 = repository.findSendersWithConnection(c, "LOC1", "D1", "T1", "NONE");
            assertThat(res3).extracting(SenderCandidate::getIdSender).contains(20);
        }
    }
}
