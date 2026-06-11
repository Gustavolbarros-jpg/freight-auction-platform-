package com.freightauction.auction.service;

import com.freightauction.auction.domain.Load;
import com.freightauction.auction.dto.CreateLoadRequest;
import com.freightauction.auction.dto.LoadResponse;
import com.freightauction.auction.mapper.LoadMapper;
import com.freightauction.auction.repository.AuctionRepository;
import com.freightauction.auction.repository.LoadRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class LoadService {
    private final LoadRepository loadRepository;
    private final LoadMapper loadMapper;
    private final AuctionRepository auctionRepository;

    public LoadService(LoadRepository loadRepository,
                       LoadMapper loadMapper,
                       AuctionRepository auctionRepository) {
        this.loadRepository = loadRepository;
        this.loadMapper = loadMapper;
        this.auctionRepository = auctionRepository;
    }

    @Transactional
    public LoadResponse create(CreateLoadRequest request, UUID createdByUserId) {
        Load load = loadMapper.toEntity(request, createdByUserId);
        return loadMapper.toResponse(loadRepository.save(load));
    }

    public List<LoadResponse> findAll() {
        return loadRepository.findAll()
                .stream()
                .map(loadMapper::toResponse)
                .toList();
    }

    public LoadResponse findById(UUID id) {
        return loadMapper.toResponse(findLoadOrThrow(id));
    }

    @Transactional
    public LoadResponse update(UUID id, CreateLoadRequest request) {
        Load load = findLoadOrThrow(id);
        loadMapper.updateEntity(request, load);
        return loadMapper.toResponse(loadRepository.save(load));
    }

    @Transactional
    public void delete(UUID id) {
        Load load = findLoadOrThrow(id);

        if (auctionRepository.existsByLoadId(id)) {
            throw new IllegalStateException("Cannot delete Load with an active Auction");
        }

        loadRepository.delete(load);
    }

    private Load findLoadOrThrow(UUID id) {
        return loadRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Load not found: " + id));
    }
}