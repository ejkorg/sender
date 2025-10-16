package com.onsemi.cim.apps.exensio.dearchiver.service;

import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSession;
import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSessionPayload;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionPayloadRepository;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties={"reloader.use-h2-external=true","external-db.allow-writes=true"})
public class SessionPushServiceIntegrationTest {

    @Autowired
    LoadSessionRepository sessionRepo;

    @Autowired
    LoadSessionPayloadRepository payloadRepo;

    @Autowired
    SessionPushService pushService;

    @Test
    public void testPushBatchH2() {
        LoadSession s = new LoadSession();
        s.setSenderId(42);
        s.setSite("EXAMPLE_SITE");
        s.setSource("test");
        s.setStatus("NEW");
        s.setTotalPayloads(2);
        sessionRepo.save(s);

        LoadSessionPayload p1 = new LoadSessionPayload(s, "M1,D1");
        LoadSessionPayload p2 = new LoadSessionPayload(s, "M2,D2");
        payloadRepo.saveAll(List.of(p1,p2));

        int pushed = pushService.pushSessionBatch(s.getId(), 10);
        assertEquals(2, pushed);

        int pushedCount = payloadRepo.countBySessionIdAndStatus(s.getId(), "PUSHED");
        assertEquals(2, pushedCount);
    }
}
