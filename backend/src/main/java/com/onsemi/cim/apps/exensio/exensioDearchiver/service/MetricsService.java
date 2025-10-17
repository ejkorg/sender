package com.onsemi.cim.apps.exensio.exensioDearchiver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {
    private final Logger log = LoggerFactory.getLogger(MetricsService.class);

    public void increment(String metric) {
        increment(metric, "");
    }

    public void increment(String metric, String tag) {
        // no-op or simple log for now
        try { if (tag == null) tag = ""; } catch (Exception ignore) {}
        log.debug("metric increment: {} {}", metric, tag);
    }
}
