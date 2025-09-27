package com.example.reloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

@Service
public class ReloaderService {

    private static final Logger logger = Logger.getLogger(ReloaderService.class.getName());

    private Map<String, Map<String, String>> dbConnections;
    private final org.springframework.core.env.Environment env;
    private final com.example.reloader.repository.LoadSessionRepository loadSessionRepository;
    private final com.example.reloader.repository.LoadSessionPayloadRepository loadSessionPayloadRepository;
    private final com.example.reloader.service.SenderService senderService;

    public ReloaderService(com.example.reloader.service.SenderService senderService,
                           com.example.reloader.repository.LoadSessionRepository loadSessionRepository,
                           com.example.reloader.repository.LoadSessionPayloadRepository loadSessionPayloadRepository,
                           org.springframework.core.env.Environment env) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        this.senderService = senderService;
        this.loadSessionRepository = loadSessionRepository;
        this.loadSessionPayloadRepository = loadSessionPayloadRepository;

        this.env = env;
        // Allow specifying an external dbconnections.json path via RELOADER_DBCONN_PATH (Spring property)
    String externalPath = com.example.reloader.config.ConfigUtils.getString(env, "reloader.dbconn.path", "RELOADER_DBCONN_PATH", null);
        if (externalPath != null && !externalPath.isBlank()) {
            try (InputStream is = Files.newInputStream(Paths.get(externalPath))) {
                dbConnections = mapper.readValue(is, Map.class);
                return;
            }
        }

        // Fallback to classpath resource for local/dev use.
        ClassPathResource resource = new ClassPathResource("dbconnections.json");
        if (resource.exists()) {
            dbConnections = mapper.readValue(resource.getInputStream(), Map.class);
        } else {
            // If no classpath resource and no external path provided, use empty map to avoid startup failure.
            dbConnections = new HashMap<>();
        }
    }

    public List<String> getSites() {
        return new ArrayList<>(dbConnections.keySet());
    }

    public String processReload(Map<String, String> params) {
        String site = params.get("site");
        String startDate = params.get("startDate");
        String endDate = params.get("endDate");
        String testerType = params.get("testerType");
        String dataType = params.get("dataType");
        String senderId = params.get("senderId");
        String numberOfDataToSend = params.get("numberOfDataToSend");
        String countLimitTrigger = params.get("countLimitTrigger");
        String listFile = params.get("listFile");
        String logFile = params.get("logFile");
        String mailTo = params.get("mailTo");
        String subject = params.get("subject");
        String jira = params.get("jira");

        Map<String, String> dbConfig = dbConnections.get(site);
        if (dbConfig == null) {
            return "Invalid site";
        }

        String host = dbConfig.get("host");
        String user = dbConfig.get("user");
        String password = dbConfig.get("password");
        String port = dbConfig.get("port");

        // Assume SID from host or something, but in Python it's sid = dbparam["sid"]
        // In dbconnections.json, some entries include a service portion like
        // "host":"myyqsq-db.onsemi.com/MYYQSQ.onsemi.com". Other entries (eg H2
        // jdbc URLs) won't contain a slash. Parse defensively so tests and dev
        // environments don't crash when the value doesn't include a service.
        String dbHost;
        String sid = "";
        if (host != null && host.contains("/")) {
            String[] hostParts = host.split("/");
            dbHost = hostParts.length > 0 ? hostParts[0] : host;
            if (hostParts.length > 1 && hostParts[1] != null) {
                String[] svcParts = hostParts[1].split("\\.");
                sid = svcParts.length > 0 ? svcParts[0] : "";
            }
        } else {
            dbHost = host;
        }

        try {
            // New flow (Option A): create a LoadSession, persist payload rows, and enqueue locally via SenderService
            com.example.reloader.entity.LoadSession session = new com.example.reloader.entity.LoadSession("ui", site, "qa", senderId == null ? null : Integer.parseInt(senderId), "reload");
            session.setStatus("DISCOVERING");
            session = loadSessionRepository.save(session);

            // If the listFile doesn't exist, query remote and write to session payloads
            if (!Files.exists(Paths.get(listFile))) {
                queryDataToSession(dbHost, user, password, sid, port, startDate, endDate, testerType, dataType, session);
            }

            // Load payloads for session and enqueue in batches using SenderService
            java.util.List<com.example.reloader.entity.LoadSessionPayload> payloads = loadSessionPayloadRepository.findBySessionId(session.getId());
            session.setTotalPayloads(payloads.size());
            session.setStatus("ENQUEUED_LOCAL");
            int batchSizeLocal = 200;
            java.util.List<String> batch = new java.util.ArrayList<>(batchSizeLocal);
            int addedCountLocal = 0;
            for (com.example.reloader.entity.LoadSessionPayload p : payloads) {
                batch.add(p.getPayloadId());
                if (batch.size() >= batchSizeLocal) {
                    com.example.reloader.service.SenderService.EnqueueResultHolder r = senderService.enqueuePayloadsWithResult(senderId == null ? null : Integer.parseInt(senderId), new java.util.ArrayList<>(batch), "metadata_discover");
                    addedCountLocal += r == null ? 0 : r.enqueuedCount;
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                com.example.reloader.service.SenderService.EnqueueResultHolder r = senderService.enqueuePayloadsWithResult(senderId == null ? null : Integer.parseInt(senderId), new java.util.ArrayList<>(batch), "metadata_discover");
                addedCountLocal += r == null ? 0 : r.enqueuedCount;
            }
            session.setEnqueuedLocalCount(addedCountLocal);
            session.setStatus("COMPLETED");
            session.setUpdatedAt(java.time.Instant.now());
            loadSessionRepository.save(session);
            return "Reload processed successfully (enqueued locally: " + addedCountLocal + ")";

        } catch (Exception e) {
            logger.severe("Error: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }

    private void queryData(String host, String user, String password, String sid, String port, String startDate, String endDate, String testerType, String dataType, String listFile) throws SQLException, IOException {
        Connection connection = DriverManager.getConnection(
            "jdbc:oracle:thin:@" + host + ":" + port + ":" + sid, user, password);
        String query = "select lot,id,id_data from all_metadata_view where end_time between (?) AND (?) and tester_type = ? and data_type = ?";
        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, startDate);
        stmt.setString(2, endDate);
        stmt.setString(3, testerType);
        stmt.setString(4, dataType);
        ResultSet rs = stmt.executeQuery();
        try {
            while (rs.next()) {
                String payload = rs.getString(2) + "," + rs.getString(3);
                // append to file for compatibility
                try (java.io.FileWriter writer = new java.io.FileWriter(listFile, true)) {
                    writer.write(payload + "\n");
                } catch (Exception ignored) {}
            }
        } finally {
            try { rs.close(); } catch (Exception ignore) {}
            try { stmt.close(); } catch (Exception ignore) {}
            try { connection.close(); } catch (Exception ignore) {}
        }
    }

    /**
     * Helper: query remote external DB and persist results into the given LoadSession via LoadSessionPayloadRepository
     */
    private void queryDataToSession(String host, String user, String password, String sid, String port, String startDate, String endDate, String testerType, String dataType, com.example.reloader.entity.LoadSession session) throws SQLException {
        Connection connection = DriverManager.getConnection(
            "jdbc:oracle:thin:@" + host + ":" + port + ":" + sid, user, password);
        String query = "select lot,id,id_data from all_metadata_view where end_time between (?) AND (?) and tester_type = ? and data_type = ?";
        PreparedStatement stmt = connection.prepareStatement(query);
        stmt.setString(1, startDate);
        stmt.setString(2, endDate);
        stmt.setString(3, testerType);
        stmt.setString(4, dataType);
        ResultSet rs = stmt.executeQuery();
        try {
            while (rs.next()) {
                String payload = rs.getString(2) + "," + rs.getString(3);
                com.example.reloader.entity.LoadSessionPayload p = new com.example.reloader.entity.LoadSessionPayload(session, payload);
                try { loadSessionPayloadRepository.save(p); } catch (Exception ignored) {}
            }
        } finally {
            try { rs.close(); } catch (Exception ignore) {}
            try { stmt.close(); } catch (Exception ignore) {}
            try { connection.close(); } catch (Exception ignore) {}
        }
    }

    private void sendMail(String mailTo, String subject, String body) {
        // Implement email sending, for now just log
        logger.info("Sending email to " + mailTo + " with subject " + subject + " body " + body);
    }

    private void removeFirstLine(String filePath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath));
        if (!lines.isEmpty()) {
            lines.remove(0);
            Files.write(Paths.get(filePath), lines);
        }
    }
}