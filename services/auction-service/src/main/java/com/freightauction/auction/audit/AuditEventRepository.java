package com.freightauction.auction.audit;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {
}