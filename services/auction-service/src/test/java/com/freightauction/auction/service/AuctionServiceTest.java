package com.freightauction.auction.service;

import com.freightauction.auction.audit.AuditService;
import com.freightauction.auction.client.BidClient;
import com.freightauction.auction.domain.Auction;
import com.freightauction.auction.domain.AuctionStatus;
import com.freightauction.auction.dto.AuctionResponse;
import com.freightauction.auction.dto.BestBidResponse;
import com.freightauction.auction.mapper.AuctionMapper;
import com.freightauction.auction.messaging.AuctionEventPublisher;
import com.freightauction.auction.repository.AuctionRepository;
import com.freightauction.auction.repository.LoadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private LoadRepository loadRepository;
    @Mock
    private AuctionMapper auctionMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private BidClient bidClient;
    @Mock
    private AuctionEventPublisher auctionEventPublisher;
    @Mock
    private AuditService auditService;

    private AuctionService auctionService;

    @BeforeEach
    void setUp() {
        auctionService = new AuctionService(
                auctionRepository,
                loadRepository,
                auctionMapper,
                redisTemplate,
                bidClient,
                auctionEventPublisher,
                auditService
        );
    }

    @Test
    void openAuctionClosesWithWinner() {
        UUID auctionId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        Auction auction = Auction.builder().id(auctionId).status(AuctionStatus.OPEN).build();
        BestBidResponse bestBid = new BestBidResponse(
                UUID.randomUUID(), auctionId, carrierId, new BigDecimal("777.77"), Instant.now()
        );
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));
        when(bidClient.findBestBid(auctionId)).thenReturn(Optional.of(bestBid));
        when(auctionRepository.save(auction)).thenReturn(auction);
        when(auctionMapper.toResponse(auction)).thenAnswer(invocation -> response(auction));

        AuctionResponse response = auctionService.close(auctionId);

        assertEquals(AuctionStatus.CLOSED, response.status());
        assertEquals(carrierId, response.winnerCarrierId());
        assertEquals(new BigDecimal("777.77"), response.winningAmount());
        verify(redisTemplate).convertAndSend(eq("auction.closed"), org.mockito.ArgumentMatchers.anyString());
        verify(auctionEventPublisher).publishAuctionClosed(any());
        verify(auditService).save(eq("AUCTION_CLOSED"), eq(auctionId), any());
    }

    @Test
    void alreadyClosedAuctionIsRejected() {
        UUID auctionId = UUID.randomUUID();
        Auction auction = Auction.builder().id(auctionId).status(AuctionStatus.CLOSED).build();
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));

        assertThrows(IllegalStateException.class, () -> auctionService.close(auctionId));

        verify(bidClient, never()).findBestBid(any());
        verify(auctionRepository, never()).save(any());
        verify(redisTemplate, never()).convertAndSend(any(), any());
        verify(auctionEventPublisher, never()).publishAuctionClosed(any());
        verify(auditService, never()).save(any(), any(), any());
    }

    private AuctionResponse response(Auction auction) {
        return new AuctionResponse(
                auction.getId(),
                null,
                auction.getStatus(),
                null,
                auction.getStartedAt(),
                auction.getClosedAt(),
                auction.getCreatedByUserId(),
                auction.getWinnerCarrierId(),
                auction.getWinningAmount()
        );
    }
}
