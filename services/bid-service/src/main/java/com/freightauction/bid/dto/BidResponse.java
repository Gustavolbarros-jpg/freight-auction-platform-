package com.freightauction.bid.dto;

import com.freightauction.bid.domain.BidStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BidResponse(
        UUID id,
        UUID auctionId,
        UUID carrierId,
        BigDecimal amount,
        BidStatus status,
        Instant receivedAt
) {
}
