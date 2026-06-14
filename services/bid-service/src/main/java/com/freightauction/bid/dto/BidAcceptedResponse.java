package com.freightauction.bid.dto;

import java.time.Instant;
import java.util.UUID;

public record BidAcceptedResponse(
        UUID bidId,
        String status,
        Instant receivedAt
) {
}
