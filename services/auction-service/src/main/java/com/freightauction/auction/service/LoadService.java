package com.freightauction.auction.service;

import com.freightauction.auction.domain.Load;
import com.freightauction.auction.dto.CreateLoadRequest;
import com.freightauction.auction.dto.LoadResponse;
import com.freightauction.auction.mapper.LoadMapper;
import com.freightauction.auction.repository.LoadRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LoadService {

    private final LoadRepository loadRepository;
    private final LoadMapper loadMapper;

    public LoadService(LoadRepository loadRepository, LoadMapper loadMapper) {
        this.loadRepository = loadRepository;
        this.loadMapper = loadMapper;
    }

    public LoadResponse create(CreateLoadRequest request, UUID createdByUserId) {
        Load load = loadMapper.toEntity(request, createdByUserId);

        Load savedLoad = loadRepository.save(load);

        return loadMapper.toResponse(savedLoad);
    }
}