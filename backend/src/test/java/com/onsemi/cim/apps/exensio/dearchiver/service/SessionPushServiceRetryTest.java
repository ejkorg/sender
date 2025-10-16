package com.onsemi.cim.apps.exensio.dearchiver.service;

import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSession;
import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSessionPayload;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionPayloadRepository;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties={"reloader.use-h2-external=true","external-db.allow-writes=true"})
public class SessionPushServiceRetryTest {

    @Autowired
    LoadSessionRepository sessionRepo;

    @Autowired
    LoadSessionPayloadRepository payloadRepo;

    @Autowired
    SessionPushService pushService;

    @Test
    public void testRetryPushesFailedPayload() {
        LoadSession s = new LoadSession();
        s.setSenderId(123);
        s.setSite("EXAMPLE_SITE");
        s.setSource("test");
        s.setStatus("NEW");
        s.setTotalPayloads(1);
        sessionRepo.save(s);

        LoadSessionPayload p = new LoadSessionPayload(s, "MRETRY,DRETRY");
        p.setStatus("FAILED");
        p.setAttempts(1);
        payloadRepo.save(p);

        int processed = pushService.retryFailed(s.getId(), 10);
        // processed should be >= 0; after retry, payload should be PUSHED
        int pushedCount = payloadRepo.countBySessionIdAndStatus(s.getId(), "PUSHED");
        assertEquals(1, pushedCount);
    }
}
