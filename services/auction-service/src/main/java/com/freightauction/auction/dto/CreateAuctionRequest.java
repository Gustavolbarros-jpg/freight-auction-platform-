package com.freightauction.auction.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAuctionRequest(
        @NotNull(message = "loadId é obrigatório")
        UUID loadId,

        @NotNull
        @Min(1)
        Integer durationMinutes
) {
}
