package com.freightauction.bid.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BidPlacedEvent(
        UUID bidId,
        UUID auctionId,
        UUID carrierId,
        BigDecimal amount,
        Instant receivedAt
) {
}
