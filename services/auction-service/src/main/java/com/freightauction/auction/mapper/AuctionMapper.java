package com.freightauction.auction.mapper;

import com.freightauction.auction.domain.Auction;
import com.freightauction.auction.domain.AuctionStatus;
import com.freightauction.auction.domain.Load;
import com.freightauction.auction.dto.AuctionResponse;
import com.freightauction.auction.dto.CreateAuctionRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper(componentModel = "spring",
        imports = {UUID.class, LocalDateTime.class, AuctionStatus.class}
)
public interface AuctionMapper {
    @Mapping(target = "id", expression = "java(UUID.randomUUID())")
    @Mapping(target = "load", source = "load")
    @Mapping(target = "status", expression = "java(AuctionStatus.OPEN)")
    @Mapping(target = "startedAt", expression = "java(LocalDateTime.now())")
    @Mapping(target = "closedAt", ignore = true)
    @Mapping(target = "createdByUserId", source = "createdByUserId")
    @Mapping(target = "winnerUserId", ignore = true)
    @Mapping(target = "createdAt", expression = "java(LocalDateTime.now())")
    Auction toEntity(CreateAuctionRequest request, Load load, UUID createdByUserId);

    @Mapping(target = "loadId", source = "auction.load.id")
    @Mapping(target = "initialPrice", source = "auction.load.initialPrice")
    AuctionResponse toResponse(Auction auction);
}
