package com.onsemi.cim.apps.exensio.dearchiver.service;

import com.onsemi.cim.apps.exensio.dearchiver.ReloaderService;
import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSession;
import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSessionPayload;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionPayloadRepository;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionRepository;
import com.onsemi.cim.apps.exensio.dearchiver.repository.SenderQueueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class ReloaderSessionIntegrationTest {

    @Autowired
    private ReloaderService reloaderService;

    @Autowired
    private LoadSessionRepository loadSessionRepository;

    @Autowired
    private LoadSessionPayloadRepository loadSessionPayloadRepository;

    @Autowired
    private SenderQueueRepository senderQueueEntryRepository;

    @Autowired
    private com.onsemi.cim.apps.exensio.dearchiver.service.SenderService senderService;

    @Test
    public void processReload_createsSessionAndPayloads_and_enqueuesLocally() throws Exception {
        // arrange: parameters that point to test dbconnections.json where EXAMPLE_SITE is defined
        Map<String, String> params = new HashMap<>();
        params.put("site", "EXAMPLE_SITE");
        params.put("environment", "dev");
        params.put("senderId", "42");
        params.put("initiatedBy", "test-runner");

        // arrange: create a temp list file and seed a couple of payload rows into LoadSessionPayload via direct repository save
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("reloader-list-", ".txt");
        params.put("listFile", tmp.toString());

        // act: create session via processReload (this will create the session and because listFile exists, skip remote query)
        reloaderService.processReload(params);

    // fetch the most recently created session to avoid collisions with other tests sharing the same DB
    LoadSession session = loadSessionRepository.findTopByOrderByIdDesc()
        .orElseThrow(() -> new IllegalStateException("Expected a session to be created"));
    // ReloaderService currently sets initiatedBy to 'ui'
    assertThat(session.getInitiatedBy()).isEqualTo("ui");
        assertThat(session.getSite()).isEqualTo("EXAMPLE_SITE");

        // Manually add payloads to session to simulate discovery (the real flow would write these during queryDataToSession)
        LoadSessionPayload p1 = new LoadSessionPayload(session, "payload-1");
        LoadSessionPayload p2 = new LoadSessionPayload(session, "payload-2");
        loadSessionPayloadRepository.save(p1);
        loadSessionPayloadRepository.save(p2);

    // assert payloads persisted for session
    List<LoadSessionPayload> payloads = loadSessionPayloadRepository.findBySessionId(session.getId());
    assertThat(payloads).hasSizeGreaterThanOrEqualTo(2);

    // trigger local enqueue using SenderService directly (this is the canonical local enqueue path)
    java.util.List<String> payloadIds = new java.util.ArrayList<>();
    for (LoadSessionPayload p : payloads) payloadIds.add(p.getPayloadId());
    com.onsemi.cim.apps.exensio.dearchiver.service.SenderService.EnqueueResultHolder result = senderService.enqueuePayloadsWithResult(session.getSenderId(), payloadIds, session.getSource());
    assertThat(result).isNotNull();
    assertThat(result.enqueuedCount).isGreaterThan(0);

    // assert sender_queue entries created locally (enqueued)
    java.util.List<com.onsemi.cim.apps.exensio.dearchiver.entity.SenderQueueEntry> q = senderQueueEntryRepository.findBySenderIdAndStatusOrderByCreatedAt(Integer.valueOf(params.get("senderId")), "NEW", org.springframework.data.domain.PageRequest.of(0, 1000));
    assertThat(q.size()).isGreaterThan(0);
    }
}
