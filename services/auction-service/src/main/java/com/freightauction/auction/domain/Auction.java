package com.freightauction.auction.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auctions")
public class Auction {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "load_id", nullable = false)
    private Load load;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuctionStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "winner_user_id")
    private UUID winnerUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}