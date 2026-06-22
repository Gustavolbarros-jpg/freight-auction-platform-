package com.freightauction.auction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BestBidResponse(
        UUID bidId,
        UUID auctionId,
        UUID carrierId,
        BigDecimal amount,
        Instant receivedAt
) {
}
