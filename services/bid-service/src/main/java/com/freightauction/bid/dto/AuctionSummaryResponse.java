package com.freightauction.bid.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AuctionSummaryResponse(UUID id, String status, BigDecimal initialPrice) {
}
