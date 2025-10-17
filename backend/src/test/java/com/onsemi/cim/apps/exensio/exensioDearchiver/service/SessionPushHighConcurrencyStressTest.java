package com.onsemi.cim.apps.exensio.exensioDearchiver.service;

import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.LoadSession;
import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.LoadSessionPayload;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.LoadSessionPayloadRepository;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.LoadSessionRepository;
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
public class SessionPushHighConcurrencyStressTest {

    @Autowired
    private LoadSessionRepository sessionRepo;

    @Autowired
    private LoadSessionPayloadRepository payloadRepo;

    @Autowired
    private SessionPushService pushService;

    @Test
    public void stressClaimNextBatchUnderConcurrency() throws Exception {
        LoadSession s = new LoadSession();
        s.setSenderId(2);
        s.setSite("STRESS_SITE");
        s.setSource("stress");
        s.setStatus("IN_PROGRESS");
        s = sessionRepo.save(s);

        int total = 200;
        for (int i = 0; i < total; i++) {
            LoadSessionPayload p = new LoadSessionPayload(s, "m" + i + ",d" + i);
            payloadRepo.save(p);
        }

        int threads = 8;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        final Set<Long> claimedIds = Collections.synchronizedSet(new HashSet<>());
        final Long sessionId = s.getId();

        Callable<Void> worker = () -> {
            while (true) {
                List<LoadSessionPayload> got = pushService.claimNextBatch(sessionId, 10);
                if (got == null || got.isEmpty()) break;
                for (LoadSessionPayload p : got) claimedIds.add(p.getId());
                // small pause to increase interleaving
                Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5));
            }
            return null;
        };

        Future<?>[] fut = new Future[threads];
        for (int i = 0; i < threads; i++) fut[i] = ex.submit(worker);

        for (int i = 0; i < threads; i++) fut[i].get(60, TimeUnit.SECONDS);
        ex.shutdownNow();

        assertThat(claimedIds.size()).isEqualTo(total);
    }
}
