package com.freightauction.auction.mapper;

import com.freightauction.auction.domain.Load;
import com.freightauction.auction.dto.CreateLoadRequest;
import com.freightauction.auction.dto.LoadResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class LoadMapper {

    public Load toEntity(CreateLoadRequest request, UUID createdByUserId) {
        return Load.builder()
                .id(UUID.randomUUID())
                .origin(request.origin())
                .destination(request.destination())
                .description(request.description())
                .weightKg(request.weightKg())
                .initialPrice(request.initialPrice())
                .createdByUserId(createdByUserId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public LoadResponse toResponse(Load load) {
        return new LoadResponse(
                load.getId(),
                load.getOrigin(),
                load.getDestination(),
                load.getDescription(),
                load.getWeightKg(),
                load.getInitialPrice(),
                load.getCreatedByUserId(),
                load.getCreatedAt()
        );
    }
}
