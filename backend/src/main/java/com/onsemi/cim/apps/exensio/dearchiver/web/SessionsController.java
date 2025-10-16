package com.onsemi.cim.apps.exensio.dearchiver.web;

import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSession;
import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSessionPayload;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionPayloadRepository;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionRepository;
import com.onsemi.cim.apps.exensio.dearchiver.service.SenderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/sessions")
public class SessionsController {
    private final LoadSessionRepository sessionRepo;
    private final LoadSessionPayloadRepository payloadRepo;
    private final SenderService senderService;

    public SessionsController(LoadSessionRepository sessionRepo, LoadSessionPayloadRepository payloadRepo, SenderService senderService) {
        this.sessionRepo = sessionRepo;
        this.payloadRepo = payloadRepo;
        this.senderService = senderService;
    }

    @GetMapping("/{id}")
    public LoadSession getSession(@PathVariable Long id) {
        return sessionRepo.findById(id).orElse(null);
    }

    @GetMapping("/{id}/payloads")
    public List<LoadSessionPayload> getPayloads(@PathVariable Long id) {
        return payloadRepo.findBySessionId(id);
    }

    @PostMapping("/{id}/push")
    public String pushSessionToExternal(@PathVariable Long id) {
        LoadSession s = sessionRepo.findById(id).orElse(null);
        if (s == null) return "session-not-found";
        List<LoadSessionPayload> payloads = payloadRepo.findBySessionId(id);
        java.util.List<com.onsemi.cim.apps.exensio.dearchiver.entity.SenderQueueEntry> items = new java.util.ArrayList<>();
        for (LoadSessionPayload p : payloads) {
            com.onsemi.cim.apps.exensio.dearchiver.entity.SenderQueueEntry e = new com.onsemi.cim.apps.exensio.dearchiver.entity.SenderQueueEntry(s.getSenderId(), p.getPayloadId(), s.getSource());
            items.add(e);
        }
        int pushed = senderService.pushToExternalQueue(s.getSite(), s.getSenderId(), items);
        s.setPushedRemoteCount(pushed);
        s.setStatus("COMPLETED");
        sessionRepo.save(s);
        return "pushed:" + pushed;
    }
}
