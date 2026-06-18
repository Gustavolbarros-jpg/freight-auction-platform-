package com.freightauction.auction.service;

import com.freightauction.auction.domain.Auction;
import com.freightauction.auction.domain.AuctionStatus;
import com.freightauction.auction.domain.Load;
import com.freightauction.auction.dto.AuctionResponse;
import com.freightauction.auction.dto.CreateAuctionRequest;
import com.freightauction.auction.event.AuctionClosedEvent;
import com.freightauction.auction.event.AuctionOpenedEvent;
import com.freightauction.auction.mapper.AuctionMapper;
import com.freightauction.auction.messaging.AuctionEventPublisher;
import com.freightauction.auction.repository.AuctionRepository;
import com.freightauction.auction.repository.LoadRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final LoadRepository loadRepository;
    private final AuctionMapper auctionMapper;
    private final AuctionEventPublisher auctionEventPublisher; // NOVO

    public AuctionService(AuctionRepository auctionRepository,
                          LoadRepository loadRepository,
                          AuctionMapper auctionMapper,
                          AuctionEventPublisher auctionEventPublisher) {
        this.auctionRepository = auctionRepository;
        this.loadRepository = loadRepository;
        this.auctionMapper = auctionMapper;
        this.auctionEventPublisher = auctionEventPublisher;
    }

    @Transactional
    public AuctionResponse create(CreateAuctionRequest request, UUID createdByUserId) {
        Load load = loadRepository.findById(request.loadId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Load not found: " + request.loadId()
                ));

        if (auctionRepository.existsByLoadIdAndStatus(load.getId(), AuctionStatus.OPEN)) {
            throw new IllegalStateException("Load already has an open Auction");
        }

        Auction saved = auctionRepository.save(auctionMapper.toEntity(request, load, createdByUserId));

        auctionEventPublisher.publishAuctionOpened(new AuctionOpenedEvent(
                saved.getId(),
                "OPEN",
                java.math.BigDecimal.ZERO
        ));

        return auctionMapper.toResponse(saved);
    }

    public List<AuctionResponse> findAll() {
        return auctionRepository.findAll()
                .stream()
                .map(auctionMapper::toResponse)
                .toList();
    }

    public AuctionResponse findById(UUID id) {
        return auctionMapper.toResponse(findAuctionOrThrow(id));
    }

    @Transactional
    public AuctionResponse close(UUID id) {
        Auction auction = findAuctionOrThrow(id);

        if (auction.getStatus() == AuctionStatus.CLOSED) {
            throw new IllegalStateException("Auction is already closed");
        }

        auction.setStatus(AuctionStatus.CLOSED);
        auction.setClosedAt(LocalDateTime.now());

        Auction saved = auctionRepository.save(auction);

        auctionEventPublisher.publishAuctionClosed(new AuctionClosedEvent(
                saved.getId(),
                "CLOSED"
        ));

        return auctionMapper.toResponse(saved);
    }

    private Auction findAuctionOrThrow(UUID id) {
        return auctionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Auction not found: " + id));
    }
}