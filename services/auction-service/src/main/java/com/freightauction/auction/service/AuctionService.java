package com.freightauction.auction.service;

import com.freightauction.auction.domain.Auction;
import com.freightauction.auction.domain.Load;
import com.freightauction.auction.dto.AuctionResponse;
import com.freightauction.auction.dto.CreateAuctionRequest;
import com.freightauction.auction.mapper.AuctionMapper;
import com.freightauction.auction.repository.AuctionRepository;
import com.freightauction.auction.repository.LoadRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final LoadRepository loadRepository;       // para buscar a Load pelo id
    private final AuctionMapper auctionMapper;

    public AuctionService(AuctionRepository auctionRepository,
                          LoadRepository loadRepository,
                          AuctionMapper auctionMapper) {
        this.auctionRepository = auctionRepository;
        this.loadRepository    = loadRepository;
        this.auctionMapper     = auctionMapper;
    }

    public AuctionResponse create(CreateAuctionRequest request, UUID createdByUserId) {
        Load load = loadRepository.findById(request.loadId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Load not found: " + request.loadId()
                ));

        Auction auction = auctionMapper.toEntity(request, load, createdByUserId);

        Auction saved = auctionRepository.save(auction);

        return auctionMapper.toResponse(saved);
    }


}
