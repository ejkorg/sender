package com.example.reloader.service;

import com.example.reloader.entity.LoadSession;
import com.example.reloader.entity.LoadSessionPayload;
import com.example.reloader.repository.LoadSessionPayloadRepository;
import com.example.reloader.repository.LoadSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class SessionPushConcurrentClaimIntegrationTest {

    @Autowired
    private LoadSessionRepository sessionRepo;

    @Autowired
    private LoadSessionPayloadRepository payloadRepo;

    @Autowired
    private SessionPushService pushService;

    @Test
    public void concurrentClaimersDoNotOverlap() throws Exception {
        LoadSession s = new LoadSession();
        s.setSenderId(1);
        s.setSite("EXAMPLE_SITE");
        s.setSource("test");
        s.setStatus("IN_PROGRESS");
        s = sessionRepo.save(s);

        int total = 50;
        for (int i = 0; i < total; i++) {
            LoadSessionPayload p = new LoadSessionPayload(s, "m" + i + ",d" + i);
            payloadRepo.save(p);
        }

        ExecutorService ex = Executors.newFixedThreadPool(2);
        final Set<Long> claimedIds = Collections.synchronizedSet(new HashSet<>());
        final Long sessionId = s.getId();

        Callable<Void> worker = () -> {
            while (true) {
                List<LoadSessionPayload> got = pushService.claimNextBatch(sessionId, 5);
                if (got == null || got.isEmpty()) break;
                for (LoadSessionPayload p : got) claimedIds.add(p.getId());
                // Simulate some processing time
                Thread.sleep(10);
            }
            return null;
        };

        Future<Void> f1 = ex.submit(worker);
        Future<Void> f2 = ex.submit(worker);

        f1.get(30, TimeUnit.SECONDS);
        f2.get(30, TimeUnit.SECONDS);
        ex.shutdownNow();

        // Assert we claimed all payloads exactly once
        assertThat(claimedIds.size()).isEqualTo(total);
    }
}
