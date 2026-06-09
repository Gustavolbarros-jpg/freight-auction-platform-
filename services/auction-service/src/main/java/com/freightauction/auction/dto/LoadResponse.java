package com.freightauction.auction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LoadResponse(
        UUID id,
        String origin,
        String destination,
        String description,
        BigDecimal weightKg,
        BigDecimal initialPrice,
        UUID createdByUserId,
        LocalDateTime createdAt
) {
}
