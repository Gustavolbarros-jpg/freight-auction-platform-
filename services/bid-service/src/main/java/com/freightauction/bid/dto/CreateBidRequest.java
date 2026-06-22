package com.freightauction.bid.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBidRequest(
        @NotNull
        UUID auctionId,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal amount
) {
}
