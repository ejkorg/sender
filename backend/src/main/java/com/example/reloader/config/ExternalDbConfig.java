package com.example.reloader.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

@Component
public class ExternalDbConfig {

    private final Map<String, Map<String, String>> dbConnections;

    public ExternalDbConfig() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        // Prefer an external file path via env var RELOADER_DBCONN_PATH for secrets in deployments.
        String externalPath = System.getenv("RELOADER_DBCONN_PATH");
        if (externalPath != null && !externalPath.isBlank()) {
            try (InputStream is = Files.newInputStream(Paths.get(externalPath))) {
                dbConnections = mapper.readValue(is, new TypeReference<>() {});
                return;
            }
        }

        // Fallback to classpath resource (for local dev only). Avoid packaging real secrets into JAR.
        ClassPathResource r = new ClassPathResource("dbconnections.json");
        dbConnections = mapper.readValue(r.getInputStream(), new TypeReference<>() {});
    }

    public Map<String, String> getConfigForSite(String site) {
        return dbConnections.get(site);
    }

    /**
     * Try resolving db config with environment qualifiers. Order: site-environment, site_environment, site.environment, site
     */
    public Map<String, String> getConfigForSite(String site, String environment) {
        if (environment != null && !environment.isBlank()) {
            String[] candidates = new String[]{String.format("%s-%s", site, environment), String.format("%s_%s", site, environment), String.format("%s.%s", site, environment), site};
            for (String k : candidates) {
                Map<String, String> cfg = dbConnections.get(k);
                if (cfg != null) return cfg;
            }
        }
        return dbConnections.get(site);
    }

    /**
     * Return the raw configuration map by key (the key used in dbconnections.json).
     */
    public Map<String, String> getConfigByKey(String key) {
        return dbConnections.get(key);
    }

    /**
     * Resolve a JDBC Connection by a connection key (db_connection_name) and optional environment.
     * This is useful when locations store a lookup key instead of full connection details.
     */
    public Connection getConnectionByKey(String key, String environment) throws SQLException {
        // DEV: support in-memory H2 external DB for offline testing. If set, return H2 connection seeded from classpath SQL.
        String useH2 = System.getenv("RELOADER_USE_H2_EXTERNAL");
        if (useH2 != null && (useH2.equalsIgnoreCase("true") || useH2.equalsIgnoreCase("1"))) {
            String h2url = "jdbc:h2:mem:external_repo;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:external_h2_seed.sql'";
            return DriverManager.getConnection(h2url, "sa", "");
        }
        if (key == null) throw new SQLException("null connection key");
        // Try key with environment qualifiers first
        Map<String, String> cfg = getConfigForSite(key, environment);
        if (cfg == null) cfg = dbConnections.get(key);
        if (cfg == null) throw new SQLException("No DB configuration for key " + key);

        String host = cfg.get("host");
        String user = cfg.get("user");
        String pw = cfg.get("password");
        String port = cfg.getOrDefault("port", "1521");

        String jdbcUrl;
        if (host != null && host.contains("/")) {
            String[] parts = host.split("/");
            String hostname = parts[0];
            String service = parts[1];
            String sid = service.split("\\.")[0];
            jdbcUrl = String.format("jdbc:oracle:thin:@%s:%s:%s", hostname, port, sid);
        } else {
            if (host != null && host.startsWith("jdbc:")) jdbcUrl = host;
            else jdbcUrl = String.format("jdbc:oracle:thin:@%s:%s", host, port);
        }

        return DriverManager.getConnection(jdbcUrl, user, pw);
    }

    /**
     * Returns a JDBC Connection for the configured site. This method attempts to be
     * DB-agnostic. For the common Oracle "host/sid" style host we build a thin URL.
     */
    public Connection getConnection(String site) throws SQLException {
        return getConnection(site, null);
    }

    public Connection getConnection(String site, String environment) throws SQLException {
        // DEV: support in-memory H2 external DB for offline testing
        String useH2 = System.getenv("RELOADER_USE_H2_EXTERNAL");
        if (useH2 != null && (useH2.equalsIgnoreCase("true") || useH2.equalsIgnoreCase("1"))) {
            String h2url = "jdbc:h2:mem:external_repo;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:external_h2_seed.sql'";
            return DriverManager.getConnection(h2url, "sa", "");
        }
        Map<String, String> cfg = getConfigForSite(site, environment);
        if (cfg == null) throw new SQLException("No DB configuration for site " + site);

        String host = cfg.get("host");
        String user = cfg.get("user");
        String pw = cfg.get("password");
        String port = cfg.getOrDefault("port", "1521");

        // If host contains a slash (host/SERVICE) assume Oracle thin format
        String jdbcUrl;
        if (host != null && host.contains("/")) {
            String[] parts = host.split("/");
            String hostname = parts[0];
            String service = parts[1];
            // If service contains dots (full domain), keep as-is for connection string
            String sid = service.split("\\.")[0];
            jdbcUrl = String.format("jdbc:oracle:thin:@%s:%s:%s", hostname, port, sid);
        } else {
            // Fallback - try as JDBC URL or generic mysql-style host
            if (host != null && host.startsWith("jdbc:")) jdbcUrl = host;
            else jdbcUrl = String.format("jdbc:oracle:thin:@%s:%s", host, port);
        }

        return DriverManager.getConnection(jdbcUrl, user, pw);
    }
}
