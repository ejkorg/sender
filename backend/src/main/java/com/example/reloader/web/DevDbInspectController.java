package com.example.reloader.web;

import com.example.reloader.config.ExternalDbConfig;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

@RestController
/**
 * Dev-only controller for inspecting and creating minimal external DB tables.
 *
 * NOTE: This controller is only active when the Spring profile 'dev' is active.
 * To enable locally set the environment or VM property:
 *
 *   -Dspring.profiles.active=dev
 *   or set environment variable SPRING_PROFILES_ACTIVE=dev
 */
@Profile("dev")
public class DevDbInspectController {
    private final ExternalDbConfig externalDbConfig;

    public DevDbInspectController(ExternalDbConfig externalDbConfig) {
        this.externalDbConfig = externalDbConfig;
    }

    // Dev-only endpoint to inspect external H2 DB used by the app.
    @GetMapping("/api/dev/db-inspect")
    public Map<String, Object> inspect(@RequestParam(defaultValue = "default") String site,
                                       @RequestParam(defaultValue = "qa") String environment) {
        Map<String, Object> out = new HashMap<>();
        try (Connection c = externalDbConfig.getConnection(site, environment); Statement s = c.createStatement()) {
            // tables to inspect
            String[] tables = new String[]{"dtp_sender", "dtp_dist_conf", "all_metadata_view", "dtp_sender_queue_item"};
            for (String t : tables) {
                try {
                    ResultSet rs = s.executeQuery("select count(*) from " + t);
                    int cnt = rs.next() ? rs.getInt(1) : 0;
                    out.put(t + ".count", cnt);
                    try (ResultSet rs2 = s.executeQuery("select * from " + t + " fetch first 5 rows only")) {
                        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
                        java.sql.ResultSetMetaData md = rs2.getMetaData();
                        while (rs2.next()) {
                            Map<String, Object> row = new HashMap<>();
                            for (int i = 1; i <= md.getColumnCount(); i++) {
                                row.put(md.getColumnName(i), rs2.getObject(i));
                            }
                            rows.add(row);
                        }
                        out.put(t + ".sample", rows);
                    } catch (Exception e) {
                        out.put(t + ".sample", java.util.List.of());
                    }
                } catch (Exception e) {
                    out.put(t + ".count", -1);
                    out.put(t + ".sample", java.util.List.of());
                }
            }
        } catch (Exception ex) {
            out.put("error", ex.getMessage());
        }
        return out;
    }

    // Dev helper: create a minimal DTP_SENDER_QUEUE_ITEM table so discovery pre-checks succeed
    @GetMapping("/api/dev/create-external-queue-table")
    public Map<String, Object> createExternalQueueTable(@RequestParam(defaultValue = "default") String site,
                                                        @RequestParam(defaultValue = "qa") String environment) {
        Map<String, Object> out = new HashMap<>();
        try (Connection c = externalDbConfig.getConnection(site, environment); Statement s = c.createStatement()) {
            // create a simple table with id_sender column; keep names compatible
            s.execute("CREATE TABLE IF NOT EXISTS DTP_SENDER_QUEUE_ITEM (id BIGINT AUTO_INCREMENT PRIMARY KEY, id_metadata VARCHAR(255), id_data VARCHAR(255), id_sender INT, record_created TIMESTAMP)");
            out.put("created", true);
        } catch (Exception ex) {
            out.put("created", false);
            out.put("error", ex.getMessage());
        }
        return out;
    }
}
