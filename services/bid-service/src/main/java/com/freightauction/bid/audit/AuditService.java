package com.freightauction.bid.audit;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    public void save(String type, UUID auctionId, Map<String, Object> payload) {
        AuditEvent event = new AuditEvent(type, "bid-service", auctionId, payload);
        auditEventRepository.save(event);
    }
}