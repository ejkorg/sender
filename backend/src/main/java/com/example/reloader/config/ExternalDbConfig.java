package com.example.reloader.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final MeterRegistry meterRegistry;

    @Autowired
    public ExternalDbConfig(Environment env, ObjectProvider<MeterRegistry> meterRegistryProvider) throws IOException {
        this.env = env;
        this.meterRegistryProvider = meterRegistryProvider;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        // metrics registry not initialized here; admin endpoints expose pool stats instead

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
                    // ensure the concurrent map doesn't hold a stale reference
                    // remove metrics for this pool if any
                    try { removeMetersForPool(key); } catch (Exception ignored) {}
                    dsCache.remove(key);
                })
                .build();

        // Prefer an external file path via env var RELOADER_DBCONN_PATH for secrets in deployments.
        String externalPath = env.getProperty("RELOADER_DBCONN_PATH");
        if (externalPath == null || externalPath.isBlank()) {
            externalPath = System.getenv("RELOADER_DBCONN_PATH");
        }
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
        dsCaffeine.invalidate(resolvedKey);
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
        // DEV: support in-memory H2 external DB for offline testing. If set, return H2 connection seeded from classpath SQL.
        String useH2 = env.getProperty("RELOADER_USE_H2_EXTERNAL");
        if (useH2 == null) useH2 = System.getenv("RELOADER_USE_H2_EXTERNAL");
        if (useH2 != null && (useH2.equalsIgnoreCase("true") || useH2.equalsIgnoreCase("1"))) {
            String h2url = "jdbc:h2:mem:external_repo;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:external_h2_seed.sql'";
            return DriverManager.getConnection(h2url, "sa", "");
        }
        if (key == null) throw new SQLException("null connection key");
        // Try key with environment qualifiers first
    Map<String, Object> cfg = getConfigForSite(key, environment);
        if (cfg == null) cfg = dbConnections.get(key);
        if (cfg == null) throw new SQLException("No DB configuration for key " + key);

    String host = cfg.get("host") == null ? null : cfg.get("host").toString();
    String user = cfg.get("user") == null ? null : cfg.get("user").toString();
    String pw = cfg.get("password") == null ? null : cfg.get("password").toString();
    String port = cfg.containsKey("port") ? cfg.get("port").toString() : "1521";

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
                Map<String, Object> perConn = cfg; // alias
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
        // DEV: support in-memory H2 external DB for offline testing
        String useH2 = System.getenv("RELOADER_USE_H2_EXTERNAL");
        if (useH2 != null && (useH2.equalsIgnoreCase("true") || useH2.equalsIgnoreCase("1"))) {
            String h2url = "jdbc:h2:mem:external_repo;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:external_h2_seed.sql'";
            return DriverManager.getConnection(h2url, "sa", "");
        }
    Map<String, Object> cfg = getConfigForSite(site, environment);
    if (cfg == null) throw new SQLException("No DB configuration for site " + site);

    String host = cfg.get("host") == null ? null : cfg.get("host").toString();
    String user = cfg.get("user") == null ? null : cfg.get("user").toString();
    String pw = cfg.get("password") == null ? null : cfg.get("password").toString();
    String port = cfg.containsKey("port") ? cfg.get("port").toString() : "1521";

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
            Map<String, Object> perConn = cfg; // alias
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
        Gauge.builder("external_db_pool_active", ds, s -> s.getHikariPoolMXBean().getActiveConnections())
            .description("Active connections in external Hikari pool")
            .tag("pool", resolvedKey)
            .register(meterRegistry);
        Gauge.builder("external_db_pool_idle", ds, s -> s.getHikariPoolMXBean().getIdleConnections())
            .description("Idle connections in external Hikari pool")
            .tag("pool", resolvedKey)
            .register(meterRegistry);
        Gauge.builder("external_db_pool_threads_waiting", ds, s -> s.getHikariPoolMXBean().getThreadsAwaitingConnection())
            .description("Threads awaiting connection in external Hikari pool")
            .tag("pool", resolvedKey)
            .register(meterRegistry);
        } catch (Exception ignored) {
            // Don't fail startup for metrics registration problems
        }
    }

    private void removeMetersForPool(String resolvedKey) {
        if (meterRegistry == null) return;
        try {
            for (Meter m : meterRegistry.getMeters()) {
                Meter.Id id = m.getId();
                String poolTag = id.getTag("pool");
                String nameTag = id.getTag("name");
                String poolNameTag = id.getTag("poolName");
                // Hikari may use a poolName like 'external-<resolvedKey>' while manual gauges use the raw resolvedKey
                String prefixed = "external-" + resolvedKey;
                if (resolvedKey.equals(poolTag) || resolvedKey.equals(nameTag) || resolvedKey.equals(poolNameTag)
                        || prefixed.equals(poolTag) || prefixed.equals(nameTag) || prefixed.equals(poolNameTag)) {
                    try { meterRegistry.remove(id); } catch (Exception ignored) {}
                }
            }
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
