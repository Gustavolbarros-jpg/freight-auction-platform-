package com.freightauction.auction.dto;

import com.freightauction.auction.domain.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionResponse(
        UUID id,
        UUID loadId,
        AuctionStatus status,
        BigDecimal initialPrice,
        LocalDateTime startedAt,
        Integer durationMinutes,
        LocalDateTime closedAt,
        UUID createdByUserId,
        UUID winnerCarrierId,
        BigDecimal winningAmount
) {
}
