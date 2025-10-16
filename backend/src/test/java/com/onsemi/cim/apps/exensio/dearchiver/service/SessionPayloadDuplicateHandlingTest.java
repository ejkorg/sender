package com.onsemi.cim.apps.exensio.dearchiver.service;

import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSession;
import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSessionPayload;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionPayloadRepository;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties={"reloader.jwt.secret=0123456789abcdef0123456789abcdef"})
public class SessionPayloadDuplicateHandlingTest {

    @Autowired
    private LoadSessionRepository sessionRepo;

    @Autowired
    private LoadSessionPayloadRepository payloadRepo;

    @Test
    public void testDuplicatePayloadSaveIsIgnored() {
        LoadSession s = new LoadSession();
        s.setSenderId(10);
        s.setSite("TEST_SITE");
        s.setSource("test");
        s.setStatus("NEW");
        s.setTotalPayloads(2);
        sessionRepo.save(s);

        LoadSessionPayload p1 = new LoadSessionPayload(s, "META,DATA");
        payloadRepo.save(p1);

        // Attempt to insert duplicate
        LoadSessionPayload p2 = new LoadSessionPayload(s, "META,DATA");
        try {
            payloadRepo.save(p2);
        } catch (Exception ignored) {}

        // Ensure only one row exists for that session+payload
    long count = payloadRepo.countBySessionIdAndPayloadId(s.getId(), "META,DATA");
    assertThat(count).isEqualTo(1);
    }
}
