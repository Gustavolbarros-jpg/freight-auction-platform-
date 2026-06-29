package com.freightauction.auction.repository;

import com.freightauction.auction.domain.Auction;
import com.freightauction.auction.domain.AuctionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {

    boolean existsByLoadId(UUID loadId);

    boolean existsByLoadIdAndStatus(UUID loadId, AuctionStatus status);

    // SELECT ... FOR UPDATE: trava a linha até o fim da transação,
    // pra impedir que duas chamadas a close() do mesmo auction sejam
    // processadas concorrentemente (closed duplicado/winner sobrescrito).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdForUpdate(UUID id);

}