package com.onsemi.cim.apps.exensio.dearchiver.service;

import com.onsemi.cim.apps.exensio.dearchiver.config.ExternalDbConfig;
import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSessionPayload;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionPayloadRepository;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * SessionPushService
 *
 * Orchestrates claiming payloads and pushing them to external databases.
 * The repository `LoadSessionPayloadRepositoryImpl` implements a JDBC-backed
 * `claimNextBatch` which performs a safe SELECT -> conditional UPDATE -> load-by-id
 * approach to avoid duplicate claims across concurrent processes.
 */
@Service
public class SessionPushService {
    private static final Logger log = LoggerFactory.getLogger(SessionPushService.class);

    private long computeBackoffMillis(int attempts) {
        // base backoff 2000ms, exponential, cap at 5 minutes
        long base = 2000L;
        int exp = Math.max(0, attempts - 1);
        long backoff = base * (1L << Math.min(exp, 20));
        long cap = 5 * 60 * 1000L;
        return Math.min(backoff, cap);
    }



    @Autowired
    private LoadSessionPayloadRepository payloadRepo;

    @Autowired
    private LoadSessionRepository sessionRepo;

    @Autowired
    private ExternalDbConfig externalDbConfig;

    @Autowired
    private Environment env;

    @Transactional
    public List<LoadSessionPayload> claimNextBatch(Long sessionId, int batchSize) {
        return payloadRepo.claimNextBatch(sessionId, batchSize);
    }

    @Transactional
    public int pushSessionBatch(Long sessionId, int batchSize) {
        // Load session
        var opt = sessionRepo.findById(sessionId);
        if (opt.isEmpty()) return 0;
        var session = opt.get();

        // Claim a batch (will mark as STAGED)
        List<LoadSessionPayload> claimed = claimNextBatch(sessionId, batchSize);
        if (claimed == null || claimed.isEmpty()) return 0;

        // Gate remote writes
        boolean allow = com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getBooleanFlag(env, "external-db.allow-writes", "EXTERNAL_DB_ALLOW_WRITES", false);
        if (!allow) {
            throw new IllegalStateException("External DB writes are disabled. Set EXTERNAL_DB_ALLOW_WRITES=true to enable");
        }

        boolean useH2 = com.onsemi.cim.apps.exensio.dearchiver.config.ConfigUtils.getBooleanFlag(env, "reloader.use-h2-external", "RELOADER_USE_H2_EXTERNAL", false);

        int pushed = 0;
        List<LoadSessionPayload> toSave = new ArrayList<>();
        try (Connection c = externalDbConfig.getConnection(session.getSite())) {
            // First consult per-site override in dbconnections.json: dbType/type/dialect
            boolean isOracle = false;
            try {
                java.util.Map<String, Object> cfg = externalDbConfig.getConfigForSite(session.getSite());
                if (cfg != null) {
                    Object t = cfg.get("dbType"); if (t == null) t = cfg.get("type"); if (t == null) t = cfg.get("dialect");
                    if (t != null && t.toString().toLowerCase().contains("oracle")) isOracle = true;
                }
            } catch (Exception cfgEx) {
                // ignore config parsing errors
            }
            // If not explicitly configured, fall back to JDBC metadata detection
            if (!isOracle) {
                try {
                    DatabaseMetaData md = c.getMetaData();
                    String dbName = md.getDatabaseProductName();
                    if (dbName != null && dbName.toLowerCase().contains("oracle")) {
                        isOracle = true;
                    }
                } catch (Exception mdEx) {
                    // ignore metadata failures and fall back to generic path
                }
            }

            if (isOracle && !useH2) {
                // Oracle path: SELECT seq.nextval FROM dual, then INSERT with that id
                for (LoadSessionPayload p : claimed) {
                    try {
                        String payload = p.getPayloadId();
                        String[] parts = payload == null ? new String[0] : payload.split(",");
                        if (parts.length < 2) {
                            p.markFailed("invalid payload format");
                            p.setAttempts(p.getAttempts() + 1);
                            p.setNextAttemptAt(Instant.now().plusMillis(computeBackoffMillis(p.getAttempts())));
                            toSave.add(p);
                            continue;
                        }
                        String meta = parts[0];
                        String data = parts[1];

                        // get next sequence value
                        long nextId = -1L;
                        try (PreparedStatement seqPs = c.prepareStatement("select DTP_SENDER_QUEUE_ITEM_SEQ.nextval from dual")) {
                            try (ResultSet rs = seqPs.executeQuery()) {
                                if (rs != null && rs.next()) {
                                    nextId = rs.getLong(1);
                                }
                            }
                        }

                        if (nextId <= 0) {
                            throw new IllegalStateException("failed to obtain sequence nextval");
                        }

                        // perform insert using the allocated id
                        String ins = "insert into DTP_SENDER_QUEUE_ITEM (id, id_metadata, id_data, id_sender, record_created) values (?, ?, ?, ?, ?)";
                        try (PreparedStatement ips = c.prepareStatement(ins)) {
                            Timestamp now = Timestamp.from(Instant.now());
                            ips.setLong(1, nextId);
                            ips.setString(2, meta);
                            ips.setString(3, data);
                            ips.setInt(4, session.getSenderId() == null ? 0 : session.getSenderId());
                            ips.setTimestamp(5, now);
                            ips.executeUpdate();
                        }

                        p.setAttempts(p.getAttempts() + 1);
                        p.markPushed(String.valueOf(nextId));
                        toSave.add(p);
                        pushed++;
                    } catch (java.sql.SQLIntegrityConstraintViolationException icv) {
                        // Treat integrity constraint violations (unique constraint on remote queue) as SKIPPED
                        log.info("Constraint violation pushing payload {}: {}", p.getPayloadId(), icv.getMessage());
                        p.setAttempts(p.getAttempts() + 1);
                        p.setStatus("SKIPPED");
                        p.setUpdatedAt(java.time.Instant.now());
                        toSave.add(p);
                    } catch (java.sql.SQLException sqlEx) {
                        // SQLState starting with '23' is integrity constraint class; classify as SKIPPED
                        String sqlState = sqlEx.getSQLState();
                        if (sqlState != null && sqlState.startsWith("23")) {
                            log.info("SQLState constraint violation pushing payload {}: {}", p.getPayloadId(), sqlEx.getMessage());
                            p.setAttempts(p.getAttempts() + 1);
                            p.setStatus("SKIPPED");
                            p.setUpdatedAt(java.time.Instant.now());
                            toSave.add(p);
                            } else {
                                log.error("Error pushing payload {} (oracle path): {}", p.getPayloadId(), sqlEx.getMessage());
                                p.markFailed(sqlEx.getMessage());
                                p.setAttempts(p.getAttempts() + 1);
                                p.setNextAttemptAt(Instant.now().plusMillis(computeBackoffMillis(p.getAttempts())));
                                toSave.add(p);
                            }
                    } catch (Exception ex) {
                        log.error("Error pushing payload {} (oracle path): {}", p.getPayloadId(), ex.getMessage());
                        p.markFailed(ex.getMessage());
                        p.setAttempts(p.getAttempts() + 1);
                        p.setNextAttemptAt(Instant.now().plusMillis(computeBackoffMillis(p.getAttempts())));
                        toSave.add(p);
                    }
                }
            } else {
                String insertSql;
                if (useH2) {
                    insertSql = "insert into DTP_SENDER_QUEUE_ITEM (id_metadata, id_data, id_sender, record_created) values (?, ?, ?, ?)";
                } else {
                    insertSql = "insert into DTP_SENDER_QUEUE_ITEM (id, id_metadata, id_data, id_sender, record_created) values (DTP_SENDER_QUEUE_ITEM_SEQ.nextval, ?, ?, ?, ?)";
                }

                try (PreparedStatement ps = c.prepareStatement(insertSql, useH2 ? PreparedStatement.RETURN_GENERATED_KEYS : Statement.RETURN_GENERATED_KEYS)) {
                    for (LoadSessionPayload p : claimed) {
                        try {
                            String payload = p.getPayloadId();
                            String[] parts = payload == null ? new String[0] : payload.split(",");
                            if (parts.length < 2) {
                                p.markFailed("invalid payload format");
                                p.setAttempts(p.getAttempts() + 1);
                                p.setNextAttemptAt(Instant.now().plusMillis(computeBackoffMillis(p.getAttempts())));
                                toSave.add(p);
                                continue;
                            }
                            String meta = parts[0];
                            String data = parts[1];

                            Timestamp now = Timestamp.from(Instant.now());
                            ps.setString(1, meta);
                            ps.setString(2, data);
                            ps.setInt(3, session.getSenderId() == null ? 0 : session.getSenderId());
                            ps.setTimestamp(4, now);
                            ps.executeUpdate();

                            // Attempt to retrieve generated id for both H2 and non-H2 using getGeneratedKeys
                            String assignedId = null;
                            try (ResultSet rs = ps.getGeneratedKeys()) {
                                if (rs != null && rs.next()) {
                                    try {
                                        long gen = rs.getLong(1);
                                        assignedId = String.valueOf(gen);
                                    } catch (Exception ignore) {
                                        // driver may return non-numeric or different column ordering
                                        assignedId = rs.getString(1);
                                    }
                                }
                            } catch (Exception gkEx) {
                                // ignore and fallback
                            }

                            // Fallback: if no generated key returned, try a safe SELECT to find the inserted row
                            if (assignedId == null) {
                                try (PreparedStatement sel = c.prepareStatement("select id from DTP_SENDER_QUEUE_ITEM where id_metadata=? and id_data=? and id_sender=? and record_created>=? order by record_created desc")) {
                                    // look for rows created at or after our timestamp (allow exact match)
                                    sel.setString(1, meta);
                                    sel.setString(2, data);
                                    sel.setInt(3, session.getSenderId() == null ? 0 : session.getSenderId());
                                    sel.setTimestamp(4, new Timestamp(now.getTime() - 2000)); // 2s leeway
                                    try (ResultSet rs2 = sel.executeQuery()) {
                                        if (rs2 != null && rs2.next()) {
                                            assignedId = rs2.getString(1);
                                        }
                                    }
                                } catch (Exception selEx) {
                                    // ignore fallback failure
                                }
                            }

                            p.setAttempts(p.getAttempts() + 1);
                            if (assignedId != null) p.markPushed(assignedId);
                            else p.markPushed(null);
                            toSave.add(p);
                            pushed++;
                        } catch (java.sql.SQLIntegrityConstraintViolationException icv) {
                            log.info("Constraint violation pushing payload {}: {}", p.getPayloadId(), icv.getMessage());
                            p.setAttempts(p.getAttempts() + 1);
                            p.setStatus("SKIPPED");
                            p.setUpdatedAt(java.time.Instant.now());
                            toSave.add(p);
                        } catch (java.sql.SQLException sqlEx) {
                            String sqlState = sqlEx.getSQLState();
                            if (sqlState != null && sqlState.startsWith("23")) {
                                log.info("SQLState constraint violation pushing payload {}: {}", p.getPayloadId(), sqlEx.getMessage());
                                p.setAttempts(p.getAttempts() + 1);
                                p.setStatus("SKIPPED");
                                p.setUpdatedAt(java.time.Instant.now());
                                toSave.add(p);
                            } else {
                                log.error("Error pushing payload {}: {}", p.getPayloadId(), sqlEx.getMessage());
                                p.markFailed(sqlEx.getMessage());
                                p.setAttempts(p.getAttempts() + 1);
                                p.setNextAttemptAt(Instant.now().plusMillis(computeBackoffMillis(p.getAttempts())));
                                toSave.add(p);
                            }
                        } catch (Exception ex) {
                            log.error("Error pushing payload {}: {}", p.getPayloadId(), ex.getMessage());
                            p.markFailed(ex.getMessage());
                            p.setAttempts(p.getAttempts() + 1);
                            p.setNextAttemptAt(Instant.now().plusMillis(computeBackoffMillis(p.getAttempts())));
                            toSave.add(p);
                        }
                    }
                }
            }
        } catch (Exception outer) {
            log.error("Error acquiring external connection or preparing statement: {}", outer.getMessage());
            // mark all claimed as failed
            for (LoadSessionPayload p : claimed) {
                p.markFailed("external connection error: " + outer.getMessage());
                p.setAttempts(p.getAttempts() + 1);
                p.setNextAttemptAt(Instant.now().plusMillis(computeBackoffMillis(p.getAttempts())));
                toSave.add(p);
            }
        }

        // persist payload status updates
        try {
            payloadRepo.saveAll(toSave);
        } catch (Exception ex) {
            log.error("Failed saving payload statuses: {}", ex.getMessage());
        }

        // update session counters
        session.setPushedRemoteCount((session.getPushedRemoteCount() == null ? 0 : session.getPushedRemoteCount()) + pushed);
        session.setFailedCount((session.getFailedCount() == null ? 0 : session.getFailedCount()) + (toSave.size() - pushed));
        sessionRepo.save(session);

        return pushed;
    }

    @Transactional
    public int retryFailed(Long sessionId, int batchSize) {
        final int MAX_ATTEMPTS = 5;
        // Page over FAILED payloads
        java.util.List<LoadSessionPayload> failed = payloadRepo.findBySessionIdAndStatusOrderById(sessionId, "FAILED", org.springframework.data.domain.PageRequest.of(0, batchSize));
        if (failed == null || failed.isEmpty()) return 0;

        java.util.List<LoadSessionPayload> toRequeue = new java.util.ArrayList<>();
        Instant now = Instant.now();
        for (LoadSessionPayload p : failed) {
            if (p.getAttempts() >= MAX_ATTEMPTS) {
                // give up
                continue;
            }
            // only requeue if nextAttemptAt is null or now >= nextAttemptAt
            Instant na = p.getNextAttemptAt();
            if (na != null && now.isBefore(na)) continue;
            // reset status to NEW so claimNextBatch can pick them up; increment attempts will happen on push
            p.setStatus("NEW");
            p.setUpdatedAt(now);
            toRequeue.add(p);
        }
        if (toRequeue.isEmpty()) return 0;

    payloadRepo.saveAll(toRequeue);
    // Flush to ensure JDBC-based claimNextBatch can see the updated rows
    payloadRepo.flush();

    // Now process by invoking pushSessionBatch which will claim and push
    return pushSessionBatch(sessionId, batchSize);
    }

    /**
     * Return lightweight session progress counts used by the controller/UI.
     */
    public java.util.Map<String,Integer> getSessionProgress(Long sessionId) {
        int total = payloadRepo.countBySessionId(sessionId);
        int countNew = payloadRepo.countBySessionIdAndStatus(sessionId, "NEW");
        int countStaged = payloadRepo.countBySessionIdAndStatus(sessionId, "STAGED");
        int countFailed = payloadRepo.countBySessionIdAndStatus(sessionId, "FAILED");
        java.util.Map<String,Integer> m = new java.util.HashMap<>();
        m.put("total", total);
        m.put("new", countNew);
        m.put("staged", countStaged);
        m.put("failed", countFailed);
        return m;
    }
}
