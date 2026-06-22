package com.freightauction.bid.service;

import com.freightauction.bid.client.AuctionClient;
import com.freightauction.bid.domain.Bid;
import com.freightauction.bid.domain.BidStatus;
import com.freightauction.bid.dto.AuctionSummaryResponse;
import com.freightauction.bid.dto.BidAcceptedResponse;
import com.freightauction.bid.dto.CreateBidRequest;
import com.freightauction.bid.event.BidPlacedEvent;
import com.freightauction.bid.messaging.BidEventPublisher;
import com.freightauction.bid.repository.BidRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class BidService {

    private static final String ACCEPTED_STATUS = "QUEUED";
    private static final String OPEN_STATUS = "OPEN";

    private final BidEventPublisher bidEventPublisher;
    private final AuctionClient auctionClient;
    private final BidRepository bidRepository;

    public BidService(BidEventPublisher bidEventPublisher, AuctionClient auctionClient, BidRepository bidRepository) {
        this.bidEventPublisher = bidEventPublisher;
        this.auctionClient = auctionClient;
        this.bidRepository = bidRepository;
    }

    public BidAcceptedResponse placeBid(CreateBidRequest request, UUID carrierId) {
        AuctionSummaryResponse auction = auctionClient.findById(request.auctionId());
        if (!OPEN_STATUS.equals(auction.status())) {
            log.warn("Bid rejected because auction is not open: auctionId={}, status={}, carrierId={}",
                    request.auctionId(), auction.status(), carrierId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Auction is not open");
        }

        log.info("Queueing bid: auctionId={}, carrierId={}, amount={}",
                request.auctionId(), carrierId, request.amount());
        UUID bidId = UUID.randomUUID();
        Instant receivedAt = Instant.now();

        Bid bid = new Bid(
                bidId,
                request.auctionId(),
                carrierId,
                request.amount(),
                BidStatus.RECEIVED,
                receivedAt
        );
        bidRepository.save(bid);

        BidPlacedEvent event = new BidPlacedEvent(
                bidId,
                request.auctionId(),
                carrierId,
                request.amount(),
                receivedAt
        );

        bidEventPublisher.publish(event);
        log.info("Bid queued: bidId={}, auctionId={}, carrierId={}, amount={}",
                bidId, request.auctionId(), carrierId, request.amount());

        return new BidAcceptedResponse(bidId, ACCEPTED_STATUS, receivedAt);
    }

    @Transactional
    public void markProcessed(UUID bidId, boolean acceptedAsBest) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new IllegalStateException("Persisted bid not found: " + bidId));
        bid.setStatus(acceptedAsBest ? BidStatus.VALIDATED : BidStatus.REJECTED);
        log.info("Bid status updated: bidId={}, status={}", bidId, bid.getStatus());
    }
}
