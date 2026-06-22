package com.freightauction.bid.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bids")
public class Bid {

    @Id
    private UUID id;

    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "user_id", nullable = false)
    private UUID carrierId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "arrival_order", insertable = false, updatable = false)
    private Long arrivalOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BidStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant receivedAt;

    protected Bid() {
    }

    public Bid(UUID id, UUID auctionId, UUID carrierId, BigDecimal amount, BidStatus status, Instant receivedAt) {
        this.id = id;
        this.auctionId = auctionId;
        this.carrierId = carrierId;
        this.amount = amount;
        this.status = status;
        this.receivedAt = receivedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public UUID getCarrierId() {
        return carrierId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getArrivalOrder() {
        return arrivalOrder;
    }

    public BidStatus getStatus() {
        return status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setStatus(BidStatus status) {
        this.status = status;
    }
}
