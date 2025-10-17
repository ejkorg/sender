package com.onsemi.cim.apps.exensio.exensioDearchiver.repository;

public class SenderCandidate {
    private final Integer idSender;
    private final String name;

    public SenderCandidate(Integer idSender, String name) {
        this.idSender = idSender;
        this.name = name;
    }

    public Integer getIdSender() { return idSender; }
    public String getName() { return name; }
}
