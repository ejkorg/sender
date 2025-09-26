package com.example.reloader.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

import jakarta.annotation.PreDestroy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

@Component
public class ExternalDbConfig {

    private final Map<String, Map<String, String>> dbConnections;
    // cache of pooled DataSources keyed by resolved connection key (e.g. "EXTERNAL-qa")
    private final ConcurrentMap<String, HikariDataSource> dsCache = new ConcurrentHashMap<>();
    private final Cache<String, HikariDataSource> dsCaffeine;

    private final Environment env;

    public ExternalDbConfig(Environment env) throws IOException {
        this.env = env;
        ObjectMapper mapper = new ObjectMapper();

        // Initialize caffeine cache for DataSources with settings from application.yml
        int maxPools = Integer.parseInt(env.getProperty("external-db.cache.max-pools", "50"));
        long expireMinutes = Long.parseLong(env.getProperty("external-db.cache.expire-after-access-minutes", "60"));
        this.dsCaffeine = Caffeine.newBuilder()
                .maximumSize(maxPools)
                .expireAfterAccess(expireMinutes, TimeUnit.MINUTES)
                .removalListener((String key, HikariDataSource ds, RemovalCause cause) -> {
                    if (ds != null) {
                        try {
                            ds.close();
                        } catch (Exception ignored) {}
                    }
                })
                .build();

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

        // Use a pooled DataSource per resolved key+env to avoid creating raw DriverManager connections each time.
        String resolvedKey = environment != null && !environment.isBlank() ? key + "-" + environment : key;
        HikariDataSource ds = dsCache.get(resolvedKey);
        if (ds == null) {
            // Try to load per-connection hikari overrides if present
            Map<String, String> perConn = cfg; // alias
            HikariConfig cfgH = new HikariConfig();
            cfgH.setJdbcUrl(jdbcUrl);
            cfgH.setUsername(user);
            cfgH.setPassword(pw);
            // Read defaults from application.yml (external-db.hikari)
            int maxPool = Integer.parseInt(env.getProperty("external-db.hikari.maximum-pool-size", "10"));
            int minIdle = Integer.parseInt(env.getProperty("external-db.hikari.minimum-idle", "1"));
            long connTimeout = Long.parseLong(env.getProperty("external-db.hikari.connection-timeout-ms", "15000"));
            long idleTimeout = Long.parseLong(env.getProperty("external-db.hikari.idle-timeout-ms", "600000"));
            long validationTimeout = Long.parseLong(env.getProperty("external-db.hikari.validation-timeout-ms", "5000"));

            // If the db config contains a nested 'hikari' map, merge overrides
            if (perConn != null && perConn.containsKey("hikari")) {
                try {
                    // the raw config map may contain nested structures; reparse as object node
                    Object obj = perConn.get("hikari");
                    // If it's a JSON string embedded, try to parse it
                    if (obj instanceof String) {
                        Map<String, Object> overrides = new ObjectMapper().readValue((String) obj, new TypeReference<>() {});
                        if (overrides.containsKey("maximumPoolSize")) maxPool = (int) ((Number) overrides.get("maximumPoolSize")).intValue();
                        if (overrides.containsKey("minimumIdle")) minIdle = (int) ((Number) overrides.get("minimumIdle")).intValue();
                        if (overrides.containsKey("connectionTimeoutMs")) connTimeout = ((Number) overrides.get("connectionTimeoutMs")).longValue();
                        if (overrides.containsKey("idleTimeoutMs")) idleTimeout = ((Number) overrides.get("idleTimeoutMs")).longValue();
                        if (overrides.containsKey("validationTimeoutMs")) validationTimeout = ((Number) overrides.get("validationTimeoutMs")).longValue();
                    }
                } catch (Exception e) {
                    // ignore and use defaults
                }
            }

            cfgH.setMaximumPoolSize(maxPool);
            cfgH.setMinimumIdle(minIdle);
            cfgH.setConnectionTimeout(connTimeout);
            cfgH.setIdleTimeout(idleTimeout);
            cfgH.setValidationTimeout(validationTimeout);
            cfgH.setPoolName("external-" + resolvedKey);

            HikariDataSource created = new HikariDataSource(cfgH);
            // store in both the plain ConcurrentMap and the Caffeine cache for eviction support
            HikariDataSource existing = dsCache.putIfAbsent(resolvedKey, created);
            HikariDataSource toUse = existing != null ? existing : created;
            dsCaffeine.put(resolvedKey, toUse);
            ds = toUse;
        }
        return ds.getConnection();
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

        // Use the top-level db connection name as key for pooling (include environment if present)
        String resolvedKey = environment != null && !environment.isBlank() ? site + "-" + environment : site;
        HikariDataSource ds = dsCache.get(resolvedKey);
        if (ds == null) {
            Map<String, String> perConn = cfg; // alias
            HikariConfig cfgH = new HikariConfig();
            cfgH.setJdbcUrl(jdbcUrl);
            cfgH.setUsername(user);
            cfgH.setPassword(pw);
            int maxPool = Integer.parseInt(env.getProperty("external-db.hikari.maximum-pool-size", "10"));
            int minIdle = Integer.parseInt(env.getProperty("external-db.hikari.minimum-idle", "1"));
            long connTimeout = Long.parseLong(env.getProperty("external-db.hikari.connection-timeout-ms", "15000"));
            long idleTimeout = Long.parseLong(env.getProperty("external-db.hikari.idle-timeout-ms", "600000"));
            long validationTimeout = Long.parseLong(env.getProperty("external-db.hikari.validation-timeout-ms", "5000"));
            if (perConn != null && perConn.containsKey("hikari")) {
                try {
                    Object obj = perConn.get("hikari");
                    if (obj instanceof String) {
                        Map<String, Object> overrides = new ObjectMapper().readValue((String) obj, new TypeReference<>() {});
                        if (overrides.containsKey("maximumPoolSize")) maxPool = (int) ((Number) overrides.get("maximumPoolSize")).intValue();
                        if (overrides.containsKey("minimumIdle")) minIdle = (int) ((Number) overrides.get("minimumIdle")).intValue();
                        if (overrides.containsKey("connectionTimeoutMs")) connTimeout = ((Number) overrides.get("connectionTimeoutMs")).longValue();
                        if (overrides.containsKey("idleTimeoutMs")) idleTimeout = ((Number) overrides.get("idleTimeoutMs")).longValue();
                        if (overrides.containsKey("validationTimeoutMs")) validationTimeout = ((Number) overrides.get("validationTimeoutMs")).longValue();
                    }
                } catch (Exception e) {
                    // ignore and use defaults
                }
            }
            cfgH.setMaximumPoolSize(maxPool);
            cfgH.setMinimumIdle(minIdle);
            cfgH.setConnectionTimeout(connTimeout);
            cfgH.setIdleTimeout(idleTimeout);
            cfgH.setValidationTimeout(validationTimeout);
            cfgH.setPoolName("external-" + resolvedKey);
            HikariDataSource created = new HikariDataSource(cfgH);
            HikariDataSource existing = dsCache.putIfAbsent(resolvedKey, created);
            HikariDataSource toUse = existing != null ? existing : created;
            dsCaffeine.put(resolvedKey, toUse);
            ds = toUse;
        }
        return ds.getConnection();
    }

    @PreDestroy
    public void destroy() {
        for (Map.Entry<String, HikariDataSource> e : dsCache.entrySet()) {
            try {
                e.getValue().close();
            } catch (Exception ignored) {}
        }
        dsCache.clear();
    }
}
