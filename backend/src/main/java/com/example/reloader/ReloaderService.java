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

    public ReloaderService() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        // Allow specifying an external dbconnections.json path via RELOADER_DBCONN_PATH.
        String externalPath = System.getenv("RELOADER_DBCONN_PATH");
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
        // In dbconnections.json, host includes sid, like "host":"myyqsq-db.onsemi.com/MYYQSQ.onsemi.com"
        // So parse host and sid
        String[] hostParts = host.split("/");
        String dbHost = hostParts[0];
        String sid = hostParts[1].split("\\.")[0]; // MYYQSQ

        try {
            // Check if list file exists
            if (!Files.exists(Paths.get(listFile))) {
                queryData(dbHost, user, password, sid, port, startDate, endDate, testerType, dataType, listFile);
            }

            // Check if complete
            try (BufferedReader reader = new BufferedReader(new FileReader(listFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if ("complete".equals(line.trim())) {
                        return "Process complete";
                    }
                }
            }

            long fileSize = Files.size(Paths.get(listFile));
            if (fileSize == 0) {
                sendMail(mailTo, subject, jira);
                try (FileWriter writer = new FileWriter(listFile, true)) {
                    writer.write("complete\n");
                }
                return "No data, emailed and marked complete";
            }

            // Connect to DB
            Connection connection = DriverManager.getConnection(
                "jdbc:oracle:thin:@" + dbHost + ":" + port + ":" + sid, user, password);

            // Check queue count
            String countQuery = "select count(id) as count from DTP_SENDER_QUEUE_ITEM where id_sender=?";
            PreparedStatement countStmt = connection.prepareStatement(countQuery);
            countStmt.setString(1, senderId);
            ResultSet rs = countStmt.executeQuery();
            rs.next();
            int count = rs.getInt(1);

            if (count < Integer.parseInt(countLimitTrigger)) {
                // Process lines
                List<String> lines = Files.readAllLines(Paths.get(listFile));
                int lineCount = 0;
                for (String line : lines) {
                    if (lineCount >= Integer.parseInt(numberOfDataToSend)) break;
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        String metadataId = parts[0];
                        String metadataIdData = parts[1];

                        // Get seq
                        String seqQuery = "select DTP_SENDER_QUEUE_ITEM_SEQ.nextval from dual";
                        PreparedStatement seqStmt = connection.prepareStatement(seqQuery);
                        ResultSet seqRs = seqStmt.executeQuery();
                        seqRs.next();
                        int seqId = seqRs.getInt(1);

                        // Insert
                        String insertQuery = "insert into DTP_SENDER_QUEUE_ITEM (id, id_metadata, id_data, id_sender, record_created) values (?, ?, ?, ?, ?)";
                        PreparedStatement insertStmt = connection.prepareStatement(insertQuery);
                        insertStmt.setInt(1, seqId);
                        insertStmt.setString(2, metadataId);
                        insertStmt.setString(3, metadataIdData);
                        insertStmt.setString(4, senderId);
                        insertStmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
                        insertStmt.executeUpdate();

                        // Remove line - rewrite file without first line
                        removeFirstLine(listFile);
                    }
                    lineCount++;
                }
            }

            connection.close();
            return "Reload processed successfully";

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
        try (FileWriter writer = new FileWriter(listFile, true)) {
            while (rs.next()) {
                writer.write(rs.getString(2) + "," + rs.getString(3) + "\n");
            }
        }
        connection.close();
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