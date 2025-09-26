package com.example.reloader.repository;

import java.time.LocalDateTime;

public class MetadataRow {
    private final String lot;
    private final String id;
    private final String idData;
    private final LocalDateTime endTime;

    public MetadataRow(String lot, String id, String idData, LocalDateTime endTime) {
        this.lot = lot;
        this.id = id;
        this.idData = idData;
        this.endTime = endTime;
    }

    public String getLot() { return lot; }
    public String getId() { return id; }
    public String getIdData() { return idData; }
    public LocalDateTime getEndTime() { return endTime; }
}
