package com.onsemi.cim.apps.exensio.dearchiver.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
// Micrometer wiring intentionally omitted here to avoid hard dependency; metrics can be added later.

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
// Micrometer wiring intentionally omitted here to avoid hard dependency; metrics can be added later.

@Component
public class ExternalDbConfig {

    private final Map<String, Map<String, Object>> dbConnections;
    // cache of pooled DataSources keyed by resolved connection key (e.g. "EXTERNAL-qa")
    private final ConcurrentMap<String, HikariDataSource> dsCache = new ConcurrentHashMap<>();
    private final Cache<String, HikariDataSource> dsCaffeine;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    // Optional MeterRegistry omitted; metrics can be registered later if desired.

    private final Environment env;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final MeterRegistry meterRegistry;
    // Keep track of Meter.Id objects we register per-resolved-pool so we can remove them reliably
    private final java.util.concurrent.ConcurrentMap<String, java.util.List<Meter.Id>> registeredMeterIds = new java.util.concurrent.ConcurrentHashMap<>();

    // no extra fields for loading temp state

    @Autowired
    public ExternalDbConfig(Environment env, ObjectProvider<MeterRegistry> meterRegistryProvider) throws IOException {
        this.env = env;
        this.meterRegistryProvider = meterRegistryProvider;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        // metrics registry not initialized here; admin endpoints expose pool stats instead

    // Initialize caffeine cache for DataSources with settings from application.yml
    int maxPools = Integer.parseInt(com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "external-db.cache.max-pools", null, "50"));
    long expireMinutes = Long.parseLong(com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "external-db.cache.expire-after-access-minutes", null, "60"));
        this.dsCaffeine = Caffeine.newBuilder()
                .maximumSize(maxPools)
                .expireAfterAccess(expireMinutes, TimeUnit.MINUTES)
                .removalListener((String key, HikariDataSource ds, RemovalCause cause) -> {
                    if (ds != null) {
                        try {
                            ds.close();
                        } catch (Exception ignored) {}
                    }
                    // ensure the concurrent map doesn't hold a stale reference
                    // remove metrics for this pool if any
                    try { removeMetersForPool(key); } catch (Exception ignored) {}
                    dsCache.remove(key);
                })
                .build();

        // Load connection definitions (JSON or YAML): external path, then YAML hint, then classpath default
        Map<String, Map<String, Object>> loadedLocal = null;
        // Prefer an external file path via env var RELOADER_DBCONN_PATH for secrets in deployments.
        String externalPath = com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "RELOADER_DBCONN_PATH", "reloader.dbconn.path", null);
        if (externalPath != null && !externalPath.isBlank()) {
            try (InputStream is = Files.newInputStream(Paths.get(externalPath))) {
                byte[] raw = is.readAllBytes();
                try {
                    loadedLocal = mapper.readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<>() {});
                } catch (Exception jsonEx) {
                    // Try YAML as a secondary format so operators can supply dbconnections.yml externally
                    try {
                        loadedLocal = yamlMapper.readValue(raw, new TypeReference<>() {});
                    } catch (Exception yamlEx) {
                        throw new IOException("Failed to parse external DB config at " + externalPath + " as JSON or YAML", yamlEx);
                    }
                }
            }
        }

        // Optional: YAML path for local convenience (e.g., classpath:dbconnections.yml or file path)
        String yamlPathProp = com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "reloader.dbconn.yaml.path", "RELOADER_DBCONN_YAML_PATH", null);
        if (loadedLocal == null && yamlPathProp != null && !yamlPathProp.isBlank()) {
            try {
                if (yamlPathProp.startsWith("classpath:")) {
                    String res = yamlPathProp.substring("classpath:".length());
                    ClassPathResource y = new ClassPathResource(res.startsWith("/") ? res.substring(1) : res);
                    try (InputStream is = y.getInputStream()) {
                        loadedLocal = yamlMapper.readValue(is, new TypeReference<>() {});
                    }
                } else {
                    try (InputStream is = Files.newInputStream(Paths.get(yamlPathProp))) {
                        loadedLocal = yamlMapper.readValue(is, new TypeReference<>() {});
                    }
                }
            } catch (Exception ignored) {
                // fall back to json classpath below
            }
        }

        // Fallback to classpath resource (for local dev only). Avoid packaging real secrets into JAR.
        if (loadedLocal == null) {
            ClassPathResource r = new ClassPathResource("dbconnections.json");
            loadedLocal = mapper.readValue(r.getInputStream(), new TypeReference<>() {});
        }
        this.dbConnections = loadedLocal;
    }

    // Backwards-compatible constructor for callers/tests that don't provide a MeterRegistry
    public ExternalDbConfig(Environment env) throws IOException {
        this(env, new ObjectProvider<MeterRegistry>() {
            @Override
            public MeterRegistry getObject(Object... args) {
                throw new UnsupportedOperationException("No MeterRegistry available");
            }

            @Override
            public MeterRegistry getObject() {
                throw new UnsupportedOperationException("No MeterRegistry available");
            }

            @Override
            public MeterRegistry getIfAvailable() {
                return null;
            }

            @Override
            public MeterRegistry getIfUnique() {
                return null;
            }
        });
    }

    // Optional meter registry setter removed; metrics can be registered by callers if desired.

    /**
     * Return a snapshot of active pools and their Hikari stats.
     */
    public Map<String, Object> listPoolStats() {
        Map<String, Object> out = new java.util.HashMap<>();
        for (Map.Entry<String, HikariDataSource> e : dsCache.entrySet()) {
            HikariDataSource ds = e.getValue();
            if (ds == null) continue;
            Map<String, Object> s = new java.util.HashMap<>();
            try {
                s.put("active", ds.getHikariPoolMXBean().getActiveConnections());
                s.put("idle", ds.getHikariPoolMXBean().getIdleConnections());
                s.put("threadsAwaiting", ds.getHikariPoolMXBean().getThreadsAwaitingConnection());
            } catch (Exception ex) {
                s.put("error", "unavailable");
            }
            out.put(e.getKey(), s);
        }
        return out;
    }

    /**
     * Return the set of active pool keys currently held in the cache.
     */
    public java.util.Set<String> getActivePoolKeys() {
        return java.util.Collections.unmodifiableSet(dsCache.keySet());
    }

    /**
     * Return the set of configured connection keys (raw keys from configuration).
     * This method exposes only the keys, not values with secrets.
     */
    public java.util.Set<String> getConfiguredKeys() {
        return java.util.Collections.unmodifiableSet(dbConnections.keySet());
    }

    // Helper used by tests to inspect the configured maximum pool size for a resolved key.
    // Package-private to avoid changing public API surface.
    int getConfiguredMaxPoolSizeForKey(String resolvedKey) {
        HikariDataSource ds = dsCache.get(resolvedKey);
        if (ds == null) return -1;
        return ds.getMaximumPoolSize();
    }

    /**
     * Force recreate (close and remove) a pool by key so it will be recreated on next use.
     */
    public void recreatePool(String resolvedKey) {
        HikariDataSource ds = dsCache.remove(resolvedKey);
        if (ds != null) {
            try { ds.close(); } catch (Exception ignored) {}
        }
        // Invalidate the Caffeine entry (this triggers the removal listener asynchronously in some runtimes)
        dsCaffeine.invalidate(resolvedKey);
        // Also call removeMetersForPool synchronously to ensure meters are removed deterministically
        try { removeMetersForPool(resolvedKey); } catch (Exception ignored) {}
        // Force a synchronous cleanup so the removal listener runs promptly during tests
        try { dsCaffeine.cleanUp(); } catch (Exception ignored) {}
    }

    public Map<String, Object> getConfigForSite(String site) {
        return dbConnections.get(site);
    }

    /**
     * Try resolving db config with environment qualifiers. Order: site-environment, site_environment, site.environment, site
     */
    public Map<String, Object> getConfigForSite(String site, String environment) {
        if (environment != null && !environment.isBlank()) {
            String[] candidates = new String[]{String.format("%s-%s", site, environment), String.format("%s_%s", site, environment), String.format("%s.%s", site, environment), site};
            for (String k : candidates) {
                Map<String, Object> cfg = dbConnections.get(k);
                if (cfg != null) return cfg;
            }
        }
        return dbConnections.get(site);
    }

    /**
     * Return the raw configuration map by key (the key used in dbconnections.json).
     */
    public Map<String, Object> getConfigByKey(String key) {
        return dbConnections.get(key);
    }

    /**
     * Resolve a JDBC Connection by a connection key (db_connection_name) and optional environment.
     * This is useful when locations store a lookup key instead of full connection details.
     */
    public Connection getConnectionByKey(String key, String environment) throws SQLException {
        if (key == null) throw new SQLException("null connection key");
        // Global H2 override for tests
        boolean useH2 = com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getBooleanFlag(env, "reloader.use-h2-external", "RELOADER_USE_H2_EXTERNAL", false);
        if (useH2) {
            String resolvedKey = environment != null && !environment.isBlank() ? key + "-" + environment : key;
            HikariDataSource ds = dsCache.get(resolvedKey);
            if (ds == null) {
                String jdbcUrl = "jdbc:h2:mem:external_repo;DB_CLOSE_DELAY=-1";
                HikariConfig cfgH = new HikariConfig();
                cfgH.setJdbcUrl(jdbcUrl);
                cfgH.setUsername("sa");
                cfgH.setPassword("");
                cfgH.setMaximumPoolSize(5);
                cfgH.setMinimumIdle(1);
                cfgH.setPoolName("external-" + resolvedKey);
                HikariDataSource created = new HikariDataSource(cfgH);
                HikariDataSource existing = dsCache.putIfAbsent(resolvedKey, created);
                HikariDataSource toUse = existing != null ? existing : created;
                dsCaffeine.put(resolvedKey, toUse);
                try (Connection c = toUse.getConnection(); Statement s = c.createStatement()) {
                    s.execute("CREATE TABLE IF NOT EXISTS DTP_SENDER_QUEUE_ITEM (id BIGINT AUTO_INCREMENT PRIMARY KEY, id_metadata VARCHAR(255), id_data VARCHAR(255), id_sender INT, record_created TIMESTAMP)");
                } catch (Exception ignored) {}
                return toUse.getConnection();
            }
            return ds.getConnection();
        }

        // Try key with environment qualifiers first
        Map<String, Object> cfg = getConfigForSite(key, environment);
        if (cfg == null) cfg = dbConnections.get(key);
        if (cfg == null) throw new SQLException("No DB configuration for key " + key);

    String host = cfg.get("host") == null ? null : cfg.get("host").toString();
    String user = cfg.get("user") == null ? null : cfg.get("user").toString();
    String pw = cfg.get("password") == null ? null : cfg.get("password").toString();
    String port = cfg.containsKey("port") ? cfg.get("port").toString() : "1521";

        String jdbcUrl;
        // Prefer explicit JDBC URL fields if present
        Object jdbcField = cfg.get("jdbc");
        if (jdbcField == null) jdbcField = cfg.get("jdbcUrl");
        if (jdbcField != null && jdbcField.toString().startsWith("jdbc:")) {
            jdbcUrl = jdbcField.toString();
        } else if (host != null && host.contains("/")) {
            String[] parts = host.split("/", 2);
            String hostPart = parts[0];
            String servicePart = parts.length > 1 ? parts[1] : "";
            String hostname = hostPart;
            String portValue = port;
            if (hostPart.contains(":")) {
                String[] hostPieces = hostPart.split(":", 2);
                hostname = hostPieces[0];
                if (hostPieces.length > 1 && !hostPieces[1].isBlank()) {
                    portValue = hostPieces[1];
                }
            }
            String serviceName = servicePart != null ? servicePart.trim() : "";
            if (serviceName.isEmpty()) {
                jdbcUrl = String.format("jdbc:oracle:thin:@%s:%s", hostname, portValue);
            } else {
                jdbcUrl = String.format("jdbc:oracle:thin:@//%s:%s/%s", hostname, portValue, serviceName);
            }
        } else {
            if (host != null && host.startsWith("jdbc:")) jdbcUrl = host;
            else if (host != null && host.contains(":")) {
                // Support host:port:SID format directly
                jdbcUrl = "jdbc:oracle:thin:@" + host;
            } else jdbcUrl = String.format("jdbc:oracle:thin:@%s:%s", host, port);
        }

        // Use a pooled DataSource per resolved key+env to avoid creating raw DriverManager connections each time.
        String resolvedKey = environment != null && !environment.isBlank() ? key + "-" + environment : key;
        HikariDataSource ds = dsCache.get(resolvedKey);
        if (ds == null) {
            // Try to load per-connection hikari overrides if present
                Map<String, Object> perConn = cfg; // alias
            HikariConfig cfgH = new HikariConfig();
            cfgH.setJdbcUrl(jdbcUrl);
            cfgH.setUsername(user);
            cfgH.setPassword(pw);
            // Read defaults from application.yml (external-db.hikari)
            int maxPool = Integer.parseInt(com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "external-db.hikari.maximum-pool-size", null, "10"));
            int minIdle = Integer.parseInt(com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "external-db.hikari.minimum-idle", null, "1"));
            long connTimeout = Long.parseLong(com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "external-db.hikari.connection-timeout-ms", null, "15000"));
            long idleTimeout = Long.parseLong(com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "external-db.hikari.idle-timeout-ms", null, "600000"));
            long validationTimeout = Long.parseLong(com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "external-db.hikari.validation-timeout-ms", null, "5000"));

            // If the db config contains a nested 'hikari' map, merge overrides.
            // Accept either a nested Map (Jackson-deserialized) or a JSON string.
            if (perConn != null && perConn.containsKey("hikari")) {
                try {
                    Object raw = perConn.get("hikari");
                    Map<String, Object> overrides = null;
                    if (raw instanceof String) {
                        overrides = mapper.readValue((String) raw, new TypeReference<>() {});
                    } else if (raw instanceof Map) {
                        //noinspection unchecked
                        overrides = (Map<String, Object>) raw;
                    }
                    if (overrides != null) {
                        if (overrides.containsKey("maximumPoolSize")) maxPool = toInt(overrides.get("maximumPoolSize"), maxPool);
                        if (overrides.containsKey("minimumIdle")) minIdle = toInt(overrides.get("minimumIdle"), minIdle);
                        if (overrides.containsKey("connectionTimeoutMs")) connTimeout = toLong(overrides.get("connectionTimeoutMs"), connTimeout);
                        if (overrides.containsKey("idleTimeoutMs")) idleTimeout = toLong(overrides.get("idleTimeoutMs"), idleTimeout);
                        if (overrides.containsKey("validationTimeoutMs")) validationTimeout = toLong(overrides.get("validationTimeoutMs"), validationTimeout);
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

            // If a MeterRegistry is available, set Hikari's Micrometer tracker factory so the pool exports metrics
            if (meterRegistry != null) {
                try {
                    cfgH.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(meterRegistry));
                } catch (NoClassDefFoundError | Exception ignored) {}
            }

            // If a MeterRegistry is available, set Hikari's Micrometer tracker factory so the pool exports metrics
            if (meterRegistry != null) {
                try {
                    cfgH.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(meterRegistry));
                } catch (NoClassDefFoundError | Exception ignored) {}
            }

            HikariDataSource created = new HikariDataSource(cfgH);
            // Register metrics only if a MeterRegistry is available (fallback manual gauges remain)
            registerHikariMetrics(created, resolvedKey);
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
        // Global H2 override for tests
        boolean useH2 = com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getBooleanFlag(env, "reloader.use-h2-external", "RELOADER_USE_H2_EXTERNAL", false);
        if (useH2) {
            String resolvedKey = environment != null && !environment.isBlank() ? site + "-" + environment : site;
            HikariDataSource ds = dsCache.get(resolvedKey);
            if (ds == null) {
                String jdbcUrl = "jdbc:h2:mem:external_repo;DB_CLOSE_DELAY=-1";
                HikariConfig cfgH = new HikariConfig();
                cfgH.setJdbcUrl(jdbcUrl);
                cfgH.setUsername("sa");
                cfgH.setPassword("");
                cfgH.setMaximumPoolSize(5);
                cfgH.setMinimumIdle(1);
                cfgH.setPoolName("external-" + resolvedKey);
                HikariDataSource created = new HikariDataSource(cfgH);
                HikariDataSource existing = dsCache.putIfAbsent(resolvedKey, created);
                HikariDataSource toUse = existing != null ? existing : created;
                dsCaffeine.put(resolvedKey, toUse);
                try (Connection c = toUse.getConnection(); Statement s = c.createStatement()) {
                    s.execute("CREATE TABLE IF NOT EXISTS DTP_SENDER_QUEUE_ITEM (id BIGINT AUTO_INCREMENT PRIMARY KEY, id_metadata VARCHAR(255), id_data VARCHAR(255), id_sender INT, record_created TIMESTAMP)");
                } catch (Exception ignored) {}
                return toUse.getConnection();
            }
            return ds.getConnection();
        }

        Map<String, Object> cfg = getConfigForSite(site, environment);
        if (cfg == null) throw new SQLException("No DB configuration for site " + site);

    String host = cfg.get("host") == null ? null : cfg.get("host").toString();
    String user = cfg.get("user") == null ? null : cfg.get("user").toString();
    String pw = cfg.get("password") == null ? null : cfg.get("password").toString();
    String port = cfg.containsKey("port") ? cfg.get("port").toString() : "1521";

        // If host contains a slash (host/SERVICE) assume Oracle thin format
        String jdbcUrl;
        Object jdbcField = cfg.get("jdbc"); if (jdbcField == null) jdbcField = cfg.get("jdbcUrl");
        if (jdbcField != null && jdbcField.toString().startsWith("jdbc:")) {
            jdbcUrl = jdbcField.toString();
        } else if (host != null && host.contains("/")) {
            String[] parts = host.split("/", 2);
            String hostPart = parts[0];
            String servicePart = parts.length > 1 ? parts[1] : "";
            String hostname = hostPart;
            String portValue = port;
            if (hostPart.contains(":")) {
                String[] hostPieces = hostPart.split(":", 2);
                hostname = hostPieces[0];
                if (hostPieces.length > 1 && !hostPieces[1].isBlank()) {
                    portValue = hostPieces[1];
                }
            }
            String serviceName = servicePart != null ? servicePart.trim() : "";
            if (serviceName.isEmpty()) {
                jdbcUrl = String.format("jdbc:oracle:thin:@%s:%s", hostname, portValue);
            } else {
                jdbcUrl = String.format("jdbc:oracle:thin:@//%s:%s/%s", hostname, portValue, serviceName);
            }
        } else {
            // Fallback - try as JDBC URL or generic mysql-style host
            if (host != null && host.startsWith("jdbc:")) jdbcUrl = host;
            else if (host != null && host.contains(":")) jdbcUrl = "jdbc:oracle:thin:@" + host;
            else jdbcUrl = String.format("jdbc:oracle:thin:@%s:%s", host, port);
        }

        // Use the top-level db connection name as key for pooling (include environment if present)
        String resolvedKey = environment != null && !environment.isBlank() ? site + "-" + environment : site;
        HikariDataSource ds = dsCache.get(resolvedKey);
        if (ds == null) {
            Map<String, Object> perConn = cfg; // alias
            HikariConfig cfgH = new HikariConfig();
            cfgH.setJdbcUrl(jdbcUrl);
            cfgH.setUsername(user);
            cfgH.setPassword(pw);
            int maxPool = Integer.parseInt(com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "external-db.hikari.maximum-pool-size", null, "10"));
            int minIdle = Integer.parseInt(com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "external-db.hikari.minimum-idle", null, "1"));
            long connTimeout = Long.parseLong(com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "external-db.hikari.connection-timeout-ms", null, "15000"));
            long idleTimeout = Long.parseLong(com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "external-db.hikari.idle-timeout-ms", null, "600000"));
            long validationTimeout = Long.parseLong(com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getString(env, "external-db.hikari.validation-timeout-ms", null, "5000"));
            if (perConn != null && perConn.containsKey("hikari")) {
                try {
                    Object raw = perConn.get("hikari");
                    Map<String, Object> overrides = null;
                    if (raw instanceof String) {
                        overrides = mapper.readValue((String) raw, new TypeReference<>() {});
                    } else if (raw instanceof Map) {
                        //noinspection unchecked
                        overrides = (Map<String, Object>) raw;
                    }
                    if (overrides != null) {
                        if (overrides.containsKey("maximumPoolSize")) maxPool = toInt(overrides.get("maximumPoolSize"), maxPool);
                        if (overrides.containsKey("minimumIdle")) minIdle = toInt(overrides.get("minimumIdle"), minIdle);
                        if (overrides.containsKey("connectionTimeoutMs")) connTimeout = toLong(overrides.get("connectionTimeoutMs"), connTimeout);
                        if (overrides.containsKey("idleTimeoutMs")) idleTimeout = toLong(overrides.get("idleTimeoutMs"), idleTimeout);
                        if (overrides.containsKey("validationTimeoutMs")) validationTimeout = toLong(overrides.get("validationTimeoutMs"), validationTimeout);
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
            // If a MeterRegistry is available, set Hikari's Micrometer tracker factory so the pool exports metrics
            if (meterRegistry != null) {
                try {
                    cfgH.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(meterRegistry));
                } catch (NoClassDefFoundError | Exception ignored) {}
            }
            HikariDataSource created = new HikariDataSource(cfgH);
            // Register manual metrics (gauge-based) only if a MeterRegistry is available
            registerHikariMetrics(created, resolvedKey);
            HikariDataSource existing = dsCache.putIfAbsent(resolvedKey, created);
            HikariDataSource toUse = existing != null ? existing : created;
            dsCaffeine.put(resolvedKey, toUse);
            ds = toUse;
        }
        return ds.getConnection();
    }

    private void registerHikariMetrics(HikariDataSource ds, String resolvedKey) {
        if (meterRegistry == null || ds == null) return;
        try {
            // Register manual gauges for pool stats using a 'pool' tag so we can remove them later
            Gauge g1 = Gauge.builder("external_db_pool_active", ds, s -> s.getHikariPoolMXBean().getActiveConnections())
                    .description("Active connections in external Hikari pool")
                    .tag("pool", resolvedKey)
                    .register(meterRegistry);
            Gauge g2 = Gauge.builder("external_db_pool_idle", ds, s -> s.getHikariPoolMXBean().getIdleConnections())
                    .description("Idle connections in external Hikari pool")
                    .tag("pool", resolvedKey)
                    .register(meterRegistry);
            Gauge g3 = Gauge.builder("external_db_pool_threads_waiting", ds, s -> s.getHikariPoolMXBean().getThreadsAwaitingConnection())
                    .description("Threads awaiting connection in external Hikari pool")
                    .tag("pool", resolvedKey)
                    .register(meterRegistry);

            // Remember the exact Meter.Id objects we registered so we can remove them reliably later
            java.util.List<Meter.Id> ids = registeredMeterIds.computeIfAbsent(resolvedKey, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
            ids.add(g1.getId()); ids.add(g2.getId()); ids.add(g3.getId());
            // registration tracked; no debug prints in production
        } catch (Exception ignored) {
            // Don't fail startup for metrics registration problems
        }
    }

    private void removeMetersForPool(String resolvedKey) {
        if (meterRegistry == null) return;
        try {
            // First attempt: remove any meters we explicitly registered and tracked earlier
            java.util.List<Meter.Id> tracked = registeredMeterIds.remove(resolvedKey);
            if (tracked != null) {
                for (Meter.Id id : tracked) {
                    try { meterRegistry.remove(id); } catch (Exception ignored) {}
                }
            }
            // Also handle the prefixed pool key if we tracked by that
            String prefixedKey = "external-" + resolvedKey;
            java.util.List<Meter.Id> trackedPref = registeredMeterIds.remove(prefixedKey);
            if (trackedPref != null) {
                for (Meter.Id id : trackedPref) {
                    try { meterRegistry.remove(id); } catch (Exception ignored) {}
                }
            }

            String prefixed = "external-" + resolvedKey;
            java.util.List<Meter> toRemove = new java.util.ArrayList<>();
            for (Meter m : meterRegistry.getMeters()) {
                Meter.Id id = m.getId();
                // DEBUG: print meter info to help diagnose deregistration issues
                // silent inspection removed; rely on tracked ids first
                // If any tag value equals the resolved key or the prefixed form, mark for removal
                boolean match = id.getTags().stream().anyMatch(t -> resolvedKey.equals(t.getValue()) || prefixed.equals(t.getValue()));
                // Also check common tag keys as a fallback, and the meter name itself
                if (!match) {
                    String poolTag = id.getTag("pool");
                    String nameTag = id.getTag("name");
                    String poolNameTag = id.getTag("poolName");
                    if (resolvedKey.equals(poolTag) || resolvedKey.equals(nameTag) || resolvedKey.equals(poolNameTag)
                            || prefixed.equals(poolTag) || prefixed.equals(nameTag) || prefixed.equals(poolNameTag)) {
                        match = true;
                    }
                }
                if (!match) {
                    String name = id.getName();
                    if (name != null && (name.contains(resolvedKey) || name.contains(prefixed))) match = true;
                }
                if (match) toRemove.add(m);
            }
            for (Meter m : toRemove) {
                try { meterRegistry.remove(m.getId()); } catch (Exception ignored) {}
            }
            // DEBUG: list remaining meters after attempted removals
            try {
                System.out.println("[DEBUG] remaining meters:");
                for (Meter m : meterRegistry.getMeters()) {
                    System.out.println("[DEBUG]  - " + m.getId().getName() + " tags=" + m.getId().getTags());
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
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

    private static int toInt(Object o, int fallback) {
        if (o == null) return fallback;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return fallback; }
    }

    private static long toLong(Object o, long fallback) {
        if (o == null) return fallback;
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return fallback; }
    }
}
