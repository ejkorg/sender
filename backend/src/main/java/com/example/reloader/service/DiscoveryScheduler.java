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
        try {
            log.info("Running scheduled discovery (site={}, senderId={})", props.getSite(), props.getSenderId());
            importer.discoverAndEnqueue(props.getSite(), props.getEnvironment(), props.getSenderId(), props.getStartDate(), props.getEndDate(), props.getTesterType(), props.getDataType(), props.getTestPhase(), props.getLocation(), null, props.isWriteListFile(), props.getNumberOfDataToSend(), props.getCountLimitTrigger());
        } catch (Exception ex) {
            log.error("Scheduled discovery failed: {}", ex.getMessage(), ex);
        }
    }
}
