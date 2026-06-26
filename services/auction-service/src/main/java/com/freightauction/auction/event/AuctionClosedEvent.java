package com.freightauction.auction.event;

import java.util.UUID;

public record AuctionClosedEvent(
        UUID auctionId,
        String status            // sempre "CLOSED"
) {
}