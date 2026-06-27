package com.freightauction.bid.auction;

import java.math.BigDecimal;
import java.util.UUID;

public record AuctionOpenedEvent(
        UUID auctionId,
        String status,
        BigDecimal initialPrice
) {
}