package com.onsemi.cim.apps.exensio.dearchiver.repository;

import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSessionPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class LoadSessionPayloadRepositoryImpl implements LoadSessionPayloadRepositoryCustom {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Override
    public List<LoadSessionPayload> claimNextBatch(Long sessionId, int batchSize) {
        final int maxAttempts = 6;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            List<Long> ids = jdbcTemplate.queryForList(
                    "SELECT id FROM load_session_payload WHERE session_id = ? AND status = 'NEW' ORDER BY id FETCH FIRST ? ROWS ONLY",
                    Long.class, sessionId, batchSize);
            if (ids == null || ids.isEmpty()) return new ArrayList<>();

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('?');
            }

            Object[] params = ids.toArray();
            String updateSql = "UPDATE load_session_payload SET status = 'STAGED', updated_at = CURRENT_TIMESTAMP WHERE id IN (" + sb.toString() + ") AND status = 'NEW'";
            int updated = jdbcTemplate.update(updateSql, params);
            if (updated == ids.size()) {
                // Load entities by id preserving JPA mapping
                List<LoadSessionPayload> claimed = entityManager.createQuery(
                        "SELECT p FROM LoadSessionPayload p WHERE p.id IN :ids", LoadSessionPayload.class)
                        .setParameter("ids", ids)
                        .getResultList();
                return claimed;
            }

            try { Thread.sleep(8 + attempt * 5L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return new ArrayList<>();
    }
}
