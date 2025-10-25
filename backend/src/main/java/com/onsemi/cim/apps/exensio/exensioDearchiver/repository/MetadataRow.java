package com.onsemi.cim.apps.exensio.exensioDearchiver.repository;

import java.time.LocalDateTime;

public class MetadataRow {
    private final String lot;
    private final String id;
    private final String idData;
    private final LocalDateTime endTime;
    private final String wafer;
    private final String originalFileName;

    public MetadataRow(String lot, String id, String idData, LocalDateTime endTime, String wafer, String originalFileName) {
        this.lot = lot;
        this.id = id;
        this.idData = idData;
        this.endTime = endTime;
        this.wafer = wafer;
        this.originalFileName = originalFileName;
    }

    public String getLot() { return lot; }
    public String getId() { return id; }
    public String getIdData() { return idData; }
    public LocalDateTime getEndTime() { return endTime; }
    public String getWafer() { return wafer; }
    public String getOriginalFileName() { return originalFileName; }
}
