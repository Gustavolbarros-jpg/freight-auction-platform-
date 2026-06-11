package com.freightauction.auction.repository;

import com.freightauction.auction.domain.Auction;
import com.freightauction.auction.domain.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    boolean existsByLoadId(UUID loadId);

    boolean existsByLoadIdAndStatus(UUID loadId, AuctionStatus status);

}
