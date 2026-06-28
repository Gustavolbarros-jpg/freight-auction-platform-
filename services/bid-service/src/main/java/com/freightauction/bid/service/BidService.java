package com.freightauction.bid.service;

import com.freightauction.bid.audit.AuditService;
import com.freightauction.bid.auction.AuctionCacheService;
import com.freightauction.bid.client.AuctionClient;
import com.freightauction.bid.domain.Bid;
import com.freightauction.bid.domain.BidStatus;
import com.freightauction.bid.dto.AuctionSummaryResponse;
import com.freightauction.bid.dto.BidAcceptedResponse;
import com.freightauction.bid.dto.BidResponse;
import com.freightauction.bid.dto.CreateBidRequest;
import com.freightauction.bid.event.BidPlacedEvent;
import com.freightauction.bid.exception.AuctionValidationException;
import com.freightauction.bid.exception.AuctionValidationException.Reason;
import com.freightauction.bid.messaging.BidEventPublisher;
import com.freightauction.bid.repository.BidRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class BidService {

    private static final String ACCEPTED_STATUS = "QUEUED";

    private final BidEventPublisher bidEventPublisher;
    private final AuctionCacheService auctionCacheService;
    private final AuctionClient auctionClient;
    private final BidRepository bidRepository;
    private final AuditService auditService;

    public BidService(BidEventPublisher bidEventPublisher,
                      AuctionCacheService auctionCacheService,
                      AuctionClient auctionClient,
                      BidRepository bidRepository,
                      AuditService auditService) {
        this.bidEventPublisher = bidEventPublisher;
        this.auctionCacheService = auctionCacheService;
        this.auctionClient = auctionClient;
        this.bidRepository = bidRepository;
        this.auditService = auditService;
    }

    public BidAcceptedResponse placeBid(CreateBidRequest request, UUID carrierId) {
        if (auctionCacheService.isUnknown(request.auctionId())) {
            synchronizeAuctionCache(request.auctionId());
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

        log.info("Queueing bid: auctionId={}, carrierId={}, amount={}",
                request.auctionId(), carrierId, request.amount());

        UUID bidId = UUID.randomUUID();
        Instant receivedAt = Instant.now();

        Bid bid = new Bid(bidId, request.auctionId(), carrierId, request.amount(), BidStatus.RECEIVED, receivedAt);
        bidRepository.save(bid);

        BidPlacedEvent event = new BidPlacedEvent(bidId, request.auctionId(), carrierId, request.amount(), receivedAt);
        bidEventPublisher.publish(event);
        auditService.save(
                "BID_RECEIVED",
                request.auctionId(),
                Map.of(
                        "bidId", bidId.toString(),
                        "carrierId", carrierId.toString(),
                        "amount", request.amount()
                )
        );

        log.info("Bid queued: bidId={}, auctionId={}, carrierId={}, amount={}",
                bidId, request.auctionId(), carrierId, request.amount());

        return new BidAcceptedResponse(bidId, ACCEPTED_STATUS, receivedAt);
    }

    private void synchronizeAuctionCache(UUID auctionId) {
        AuctionSummaryResponse auction = auctionClient.findById(auctionId);

        if ("OPEN".equals(auction.status())) {
            if (auction.initialPrice() == null) {
                throw new AuctionValidationException(
                        Reason.AUCTION_NOT_SYNCED,
                        "Auction initial price not synchronized — please retry in a moment"
                );
            }

            auctionCacheService.saveAsOpen(auction.id(), auction.initialPrice());
            log.info("Auction cache hydrated from auction-service: auctionId={}, status={}", auction.id(), auction.status());
            return;
        }

        auctionCacheService.saveAsClosed(auction.id());
        log.info("Auction cache hydrated as closed from auction-service: auctionId={}, status={}", auction.id(), auction.status());
    }

    public List<BidResponse> findByAuctionId(UUID auctionId) {
        return bidRepository.findByAuctionIdOrderByReceivedAtDesc(auctionId)
                .stream()
                .map(bid -> new BidResponse(
                        bid.getId(),
                        bid.getAuctionId(),
                        bid.getCarrierId(),
                        bid.getAmount(),
                        bid.getStatus(),
                        bid.getReceivedAt()
                ))
                .toList();
    }

    @Transactional
    public void markProcessed(UUID bidId, boolean acceptedAsBest) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new IllegalStateException("Persisted bid not found: " + bidId));
        bid.setStatus(acceptedAsBest ? BidStatus.VALIDATED : BidStatus.REJECTED);
        auditService.save(
                acceptedAsBest ? "BID_VALIDATED" : "BID_REJECTED",
                bid.getAuctionId(),
                Map.of(
                        "bidId", bid.getId().toString(),
                        "carrierId", bid.getCarrierId().toString(),
                        "amount", bid.getAmount(),
                        "status", bid.getStatus().name()
                )
        );
        log.info("Bid status updated: bidId={}, status={}", bidId, bid.getStatus());
    }
}
