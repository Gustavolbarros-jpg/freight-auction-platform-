package com.freightauction.auction.repository;

import com.freightauction.auction.domain.Auction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {
}
