package com.freightauction.bid.service;

import com.freightauction.bid.audit.AuditService;
import com.freightauction.bid.auction.AuctionCacheService;
import com.freightauction.bid.domain.Bid;
import com.freightauction.bid.domain.BidStatus;
import com.freightauction.bid.dto.BidAcceptedResponse;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class BidService {

    private static final String ACCEPTED_STATUS = "QUEUED";

    private final BidEventPublisher bidEventPublisher;
    private final AuctionCacheService auctionCacheService;
    private final BidRepository bidRepository;
    private final AuditService auditService;

    public BidService(BidEventPublisher bidEventPublisher,
                      AuctionCacheService auctionCacheService,
                      BidRepository bidRepository,
                      AuditService auditService) {
        this.bidEventPublisher = bidEventPublisher;
        this.auctionCacheService = auctionCacheService;
        this.bidRepository = bidRepository;
        this.auditService = auditService;
    }

    public BidAcceptedResponse placeBid(CreateBidRequest request, UUID carrierId) {
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

        log.info("Queueing bid: auctionId={}, carrierId={}, amount={}",
                request.auctionId(), carrierId, request.amount());

        UUID bidId = UUID.randomUUID();
        Instant receivedAt = Instant.now();

        Bid bid = new Bid(bidId, request.auctionId(), carrierId, request.amount(), BidStatus.RECEIVED, receivedAt);
        bidRepository.save(bid);

        BidPlacedEvent event = new BidPlacedEvent(bidId, request.auctionId(), carrierId, request.amount(), receivedAt);
        bidEventPublisher.publish(event);

        try {
            auditService.save(
                    "BID_RECEIVED",
                    request.auctionId(),
                    Map.of(
                            "bidId", bidId.toString(),
                            "carrierId", carrierId.toString(),
                            "amount", request.amount()
                    )
            );
        } catch (Exception e) {
            log.warn("Falha ao salvar evento de auditoria BID_RECEIVED: {}", e.getMessage());
        }

        log.info("Bid queued: bidId={}, auctionId={}, carrierId={}, amount={}",
                bidId, request.auctionId(), carrierId, request.amount());

        return new BidAcceptedResponse(bidId, ACCEPTED_STATUS, receivedAt);
    }

    @Transactional
    public void markProcessed(UUID bidId, boolean acceptedAsBest) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new IllegalStateException("Persisted bid not found: " + bidId));
        bid.setStatus(acceptedAsBest ? BidStatus.VALIDATED : BidStatus.REJECTED);

        try {
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
        } catch (Exception e) {
            log.warn("Falha ao salvar evento de auditoria {}: {}", 
                    acceptedAsBest ? "BID_VALIDATED" : "BID_REJECTED", e.getMessage());
        }

        log.info("Bid status updated: bidId={}, status={}", bidId, bid.getStatus());
    }
}