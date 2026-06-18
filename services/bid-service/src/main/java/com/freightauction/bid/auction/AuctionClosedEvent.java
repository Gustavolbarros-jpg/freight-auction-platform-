package com.freightauction.bid.auction;

import java.util.UUID;

public record AuctionClosedEvent(
        UUID auctionId,
        String status
) {
}