package com.freightauction.bid.service;

import com.freightauction.bid.auction.AuctionCacheService;
import com.freightauction.bid.dto.BidAcceptedResponse;
import com.freightauction.bid.dto.CreateBidRequest;
import com.freightauction.bid.event.BidPlacedEvent;
import com.freightauction.bid.exception.AuctionValidationException;
import com.freightauction.bid.exception.AuctionValidationException.Reason;
import com.freightauction.bid.messaging.BidEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class BidService {

    private static final String ACCEPTED_STATUS = "QUEUED";

    private final BidEventPublisher bidEventPublisher;
    private final AuctionCacheService auctionCacheService; // NOVO

    public BidService(BidEventPublisher bidEventPublisher,
                      AuctionCacheService auctionCacheService) {
        this.bidEventPublisher = bidEventPublisher;
        this.auctionCacheService = auctionCacheService;
    }

    public BidAcceptedResponse placeBid(CreateBidRequest request) {
        if (auctionCacheService.isUnknown(request.auctionId())) {
            throw new AuctionValidationException(
                    Reason.AUCTION_NOT_SYNCED,
                    "Auction not yet synchronized — please retry in a moment"
            );
        }

        if (!auctionCacheService.isOpen(request.auctionId())) {
            throw new AuctionValidationException(
                    Reason.AUCTION_CLOSED,
                    "Auction is closed and no longer accepting bids"
            );
        }

        Optional<BigDecimal> initialPrice = auctionCacheService.getInitialPrice(request.auctionId());
        initialPrice.ifPresent(price -> {
            if (request.amount().compareTo(price) >= 0) {
                throw new AuctionValidationException(
                        Reason.AMOUNT_TOO_HIGH,
                        "Bid amount must be lower than the auction initial price of " + price
                );
            }
        });

        UUID bidId = UUID.randomUUID();
        Instant receivedAt = Instant.now();

        BidPlacedEvent event = new BidPlacedEvent(
                bidId,
                request.auctionId(),
                request.carrierId(),
                request.amount(),
                receivedAt
        );

        bidEventPublisher.publish(event);

        return new BidAcceptedResponse(bidId, ACCEPTED_STATUS, receivedAt);
    }
}