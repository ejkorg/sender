package com.example.reloader.service;

import com.example.reloader.config.DiscoveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryScheduler {
    private final Logger log = LoggerFactory.getLogger(DiscoveryScheduler.class);
    private final MetadataImporterService importer;
    private final DiscoveryProperties props;

    public DiscoveryScheduler(MetadataImporterService importer, DiscoveryProperties props) {
        this.importer = importer;
        this.props = props;
    }

    @Scheduled(cron = "${app.discovery.cron:0 */5 * * * *}")
    public void scheduledDiscovery() {
        if (!props.isEnabled()) {
            return;
        }

        String site = props.getSite();
        if (site == null || site.isBlank()) {
            log.warn("Skipping scheduled discovery because no site is configured");
            return;
        }

        try {
            log.info("Running scheduled discovery (site={}, senderId={})", site, props.getSenderId());
            importer.discoverAndEnqueue(site, props.getEnvironment(), props.getSenderId(), props.getStartDate(), props.getEndDate(), props.getTesterType(), props.getDataType(), props.getTestPhase(), props.getLocation(), null, props.isWriteListFile(), props.getNumberOfDataToSend(), props.getCountLimitTrigger());
        } catch (Exception ex) {
            log.error("Scheduled discovery failed: {}", ex.getMessage(), ex);
        }
    }
}
