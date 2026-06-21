package com.freightauction.auction.service;

import com.freightauction.auction.domain.Auction;
import com.freightauction.auction.domain.AuctionStatus;
import com.freightauction.auction.domain.Load;
import com.freightauction.auction.dto.AuctionResponse;
import com.freightauction.auction.dto.CreateAuctionRequest;
import com.freightauction.auction.mapper.AuctionMapper;
import com.freightauction.auction.repository.AuctionRepository;
import com.freightauction.auction.repository.LoadRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AuctionService {
    private final AuctionRepository auctionRepository;
    private final LoadRepository loadRepository;       // para buscar a Load pelo id
    private final AuctionMapper auctionMapper;
    private final StringRedisTemplate redisTemplate;

    public AuctionService(AuctionRepository auctionRepository,
                          LoadRepository loadRepository,
                          AuctionMapper auctionMapper,
                          StringRedisTemplate redisTemplate) {
        this.auctionRepository = auctionRepository;
        this.loadRepository = loadRepository;
        this.auctionMapper = auctionMapper;
        this.redisTemplate = redisTemplate;
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

        auction.setStatus(AuctionStatus.CLOSED);
        auction.setClosedAt(LocalDateTime.now());

        Auction saved = auctionRepository.save(auction);

        redisTemplate.convertAndSend(
                "auction.closed",
                serializeClosedAuction(saved)
        );

        log.info("Auction closed: auctionId={}, closedAt={}", saved.getId(), saved.getClosedAt());
        return auctionMapper.toResponse(saved);
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
                  "closedAt": "%s"
                }
                """.formatted(
                auction.getId(),
                auction.getStatus(),
                auction.getClosedAt()
        );
    }

}
