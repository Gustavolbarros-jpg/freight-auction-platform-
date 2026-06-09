package com.freightauction.auction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateLoadRequest(
        @NotBlank
        String origin,

        @NotBlank
        String destination,

        String description,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal weightKg,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal initialPrice
) {
}
