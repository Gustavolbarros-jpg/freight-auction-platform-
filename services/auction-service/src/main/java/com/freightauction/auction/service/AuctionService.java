package com.freightauction.auction.service;

import com.freightauction.auction.audit.AuditService;
import com.freightauction.auction.client.BidClient;
import com.freightauction.auction.domain.Auction;
import com.freightauction.auction.domain.AuctionStatus;
import com.freightauction.auction.domain.Load;
import com.freightauction.auction.dto.AuctionResponse;
import com.freightauction.auction.dto.BestBidResponse;
import com.freightauction.auction.dto.CreateAuctionRequest;
import com.freightauction.auction.event.AuctionClosedEvent;
import com.freightauction.auction.event.AuctionOpenedEvent;
import com.freightauction.auction.mapper.AuctionMapper;
import com.freightauction.auction.messaging.AuctionEventPublisher;
import com.freightauction.auction.repository.AuctionRepository;
import com.freightauction.auction.repository.LoadRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final LoadRepository loadRepository;
    private final AuctionMapper auctionMapper;
    private final StringRedisTemplate redisTemplate;
    private final BidClient bidClient;
    private final AuctionEventPublisher auctionEventPublisher;
    private final AuditService auditService;


    public AuctionService(AuctionRepository auctionRepository,
                          LoadRepository loadRepository,
                          AuctionMapper auctionMapper,
                          StringRedisTemplate redisTemplate,
                          BidClient bidClient,
                          AuctionEventPublisher auctionEventPublisher,
                          AuditService auditService) {
        this.auctionRepository = auctionRepository;
        this.loadRepository = loadRepository;
        this.auctionMapper = auctionMapper;
        this.redisTemplate = redisTemplate;
        this.bidClient = bidClient;
        this.auctionEventPublisher = auctionEventPublisher;
        this.auditService = auditService;
    }

    @Transactional
    public AuctionResponse create(CreateAuctionRequest request, UUID createdByUserId) {
        log.info("Creating auction: loadId={}, createdByUserId={}", request.loadId(), createdByUserId);
        Load load = loadRepository.findById(request.loadId())
                .orElseThrow(() -> {
                    log.warn("Auction creation rejected: loadId={} not found", request.loadId());
                    return new IllegalArgumentException("Load not found: " + request.loadId());
                });

        if (auctionRepository.existsByLoadIdAndStatus(load.getId(), AuctionStatus.OPEN)) {
            log.warn("Auction creation rejected: loadId={} already has an open auction", load.getId());
            throw new IllegalStateException("Load already has an open Auction");
        }

        Auction saved = auctionRepository.save(auctionMapper.toEntity(request, load, createdByUserId));

        auctionEventPublisher.publishAuctionOpened(new AuctionOpenedEvent(
                saved.getId(),
                "OPEN",
                saved.getLoad().getInitialPrice()
        ));
        auditService.save(
                "AUCTION_OPENED",
                saved.getId(),
                Map.of(
                        "loadId", saved.getLoad().getId().toString(),
                        "createdByUserId", saved.getCreatedByUserId().toString(),
                        "initialPrice", saved.getLoad().getInitialPrice()
                )
        );

        log.info("Auction created: auctionId={}, loadId={}, createdByUserId={}",
                saved.getId(), load.getId(), createdByUserId);
        return auctionMapper.toResponse(saved);
    }

    public List<AuctionResponse> findAll() {
        List<AuctionResponse> auctions = auctionRepository.findAll()
                .stream()
                .map(auctionMapper::toResponse)
                .toList();
        log.info("Auctions listed: count={}", auctions.size());
        return auctions;
    }

    public AuctionResponse findById(UUID id) {
        log.info("Finding auction: auctionId={}", id);
        return auctionMapper.toResponse(findAuctionOrThrow(id));
    }

    @Transactional
    public AuctionResponse close(UUID id) {
        log.info("Closing auction: auctionId={}", id);
        Auction auction = findAuctionOrThrow(id);

        if (auction.getStatus() == AuctionStatus.CLOSED) {
            log.warn("Auction closing rejected: auctionId={} is already closed", id);
            throw new IllegalStateException("Auction is already closed");
        }

        bidClient.findBestBid(id).ifPresent(bestBid -> applyWinner(auction, bestBid));

        auction.setStatus(AuctionStatus.CLOSED);
        auction.setClosedAt(LocalDateTime.now());

        Auction saved = auctionRepository.save(auction);

        redisTemplate.convertAndSend("auction.closed", serializeClosedAuction(saved));

        auctionEventPublisher.publishAuctionClosed(new AuctionClosedEvent(
                saved.getId(),
                "CLOSED"
        ));

        Map<String, Object> payload = new HashMap<>();
        payload.put("winnerCarrierId", saved.getWinnerCarrierId() == null ? null : saved.getWinnerCarrierId().toString());
        payload.put("winningAmount", saved.getWinningAmount());
        payload.put("closedAt", saved.getClosedAt().toString());
        auditService.save("AUCTION_CLOSED", saved.getId(), payload);

        log.info("Auction closed: auctionId={}, closedAt={}, winnerCarrierId={}, winningAmount={}",
                saved.getId(), saved.getClosedAt(), saved.getWinnerCarrierId(), saved.getWinningAmount());
        return auctionMapper.toResponse(saved);
    }

    private void applyWinner(Auction auction, BestBidResponse bestBid) {
        auction.setWinnerCarrierId(bestBid.carrierId());
        auction.setWinningAmount(bestBid.amount());
    }

    private Auction findAuctionOrThrow(UUID id) {
        return auctionRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Auction not found: auctionId={}", id);
                    return new IllegalArgumentException("Auction not found: " + id);
                });
    }

    private String serializeClosedAuction(Auction auction) {
        return """
                {
                  "auctionId": "%s",
                  "status": "%s",
                  "closedAt": "%s",
                  "winnerCarrierId": %s,
                  "winningAmount": %s
                }
                """.formatted(
                auction.getId(),
                auction.getStatus(),
                auction.getClosedAt(),
                auction.getWinnerCarrierId() == null ? "null" : "\"" + auction.getWinnerCarrierId() + "\"",
                auction.getWinningAmount() == null ? "null" : auction.getWinningAmount()
        );
    }
}
