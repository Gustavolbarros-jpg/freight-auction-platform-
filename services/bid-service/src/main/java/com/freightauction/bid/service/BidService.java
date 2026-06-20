package com.freightauction.bid.service;

import com.freightauction.bid.dto.BidAcceptedResponse;
import com.freightauction.bid.dto.CreateBidRequest;
import com.freightauction.bid.event.BidPlacedEvent;
import com.freightauction.bid.messaging.BidEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class BidService {

    private static final String ACCEPTED_STATUS = "QUEUED";

    private final BidEventPublisher bidEventPublisher;

    public BidService(BidEventPublisher bidEventPublisher) {
        this.bidEventPublisher = bidEventPublisher;
    }

    public BidAcceptedResponse placeBid(CreateBidRequest request) {
        log.info("Queueing bid: auctionId={}, carrierId={}, amount={}", request.auctionId(), request.carrierId(), request.amount());
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
        log.info("Bid queued: bidId={}, auctionId={}, carrierId={}, amount={}", bidId, request.auctionId(), request.carrierId(), request.amount());

        return new BidAcceptedResponse(bidId, ACCEPTED_STATUS, receivedAt);
    }
}
