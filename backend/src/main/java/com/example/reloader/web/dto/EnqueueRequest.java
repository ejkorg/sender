package com.example.reloader.web.dto;

import java.util.List;

public class EnqueueRequest {
    private Integer senderId;
    private List<String> payloadIds;
    private String source;

    public EnqueueRequest() {}

    public Integer getSenderId() { return senderId; }
    public void setSenderId(Integer senderId) { this.senderId = senderId; }
    public List<String> getPayloadIds() { return payloadIds; }
    public void setPayloadIds(List<String> payloadIds) { this.payloadIds = payloadIds; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
