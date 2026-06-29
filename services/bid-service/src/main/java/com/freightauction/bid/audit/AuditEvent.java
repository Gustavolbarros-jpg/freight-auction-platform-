package com.freightauction.bid.audit;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Document(collection = "events")
public class AuditEvent {

    @Id
    private String id;

    private String type;

    private String service;

    private String auctionId;

    private Map<String, Object> payload;

    private Instant timestamp;

    public AuditEvent() {
    }

    public AuditEvent(String type, String service, UUID auctionId, Map<String, Object> payload) {
        this.type = type;
        this.service = service;
        this.auctionId = auctionId.toString();
        this.payload = payload;
        this.timestamp = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getService() {
        return service;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}