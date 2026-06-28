package com.freightauction.bid.repository;

import com.freightauction.bid.domain.Bid;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {
    List<Bid> findByAuctionIdOrderByReceivedAtDesc(UUID auctionId);
}
