package com.freightauction.bid.service;

import com.freightauction.bid.dto.BidAcceptedResponse;
import com.freightauction.bid.dto.CreateBidRequest;
import com.freightauction.bid.event.BidPlacedEvent;
import com.freightauction.bid.messaging.BidEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class BidService {

    private static final String ACCEPTED_STATUS = "QUEUED";

    private final BidEventPublisher bidEventPublisher;

    public BidService(BidEventPublisher bidEventPublisher) {
        this.bidEventPublisher = bidEventPublisher;
    }

    public BidAcceptedResponse placeBid(CreateBidRequest request) {
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
