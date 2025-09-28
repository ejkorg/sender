package com.example.reloader.service;

import com.example.reloader.entity.LoadSession;
import com.example.reloader.entity.LoadSessionPayload;
import com.example.reloader.repository.LoadSessionPayloadRepository;
import com.example.reloader.repository.LoadSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties={"reloader.use-h2-external=true","external-db.allow-writes=true"})
public class SessionPushServiceBackoffRetryTest {

    @Autowired
    LoadSessionRepository sessionRepo;

    @Autowired
    LoadSessionPayloadRepository payloadRepo;

    @Autowired
    SessionPushService pushService;

    @Test
    public void testRetryRespectsBackoff() {
        LoadSession s = new LoadSession();
        s.setSenderId(9);
        s.setSite("BACKOFF_SITE");
        s.setSource("test");
        s.setStatus("NEW");
        s.setTotalPayloads(1);
        sessionRepo.save(s);

        LoadSessionPayload p = new LoadSessionPayload(s, "M2,D2");
        p.setStatus("FAILED");
        p.setAttempts(1);
        // set nextAttemptAt in the future
        p.setNextAttemptAt(Instant.now().plusSeconds(60));
        payloadRepo.save(p);

        int r = pushService.retryFailed(s.getId(), 10);
        // should not process because backoff not elapsed
        assertEquals(0, r);

        // set nextAttemptAt in the past and try again
        p.setNextAttemptAt(Instant.now().minusSeconds(10));
        payloadRepo.save(p);

        int r2 = pushService.retryFailed(s.getId(), 10);
        // should process and push the payload now (H2 path)
        assertEquals(1, r2);
    }
}
