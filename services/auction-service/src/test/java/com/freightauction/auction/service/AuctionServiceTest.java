package com.freightauction.auction.service;

import com.freightauction.auction.audit.AuditService;
import com.freightauction.auction.client.BidClient;
import com.freightauction.auction.domain.Auction;
import com.freightauction.auction.domain.AuctionStatus;
import com.freightauction.auction.domain.Load;
import com.freightauction.auction.dto.AuctionResponse;
import com.freightauction.auction.dto.BestBidResponse;
import com.freightauction.auction.dto.CreateAuctionRequest;
import com.freightauction.auction.mapper.AuctionMapper;
import com.freightauction.auction.messaging.AuctionEventPublisher;
import com.freightauction.auction.repository.AuctionRepository;
import com.freightauction.auction.repository.LoadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @Mock private AuctionRepository auctionRepository;
    @Mock private LoadRepository loadRepository;
    @Mock private AuctionMapper auctionMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private BidClient bidClient;
    @Mock private AuctionEventPublisher auctionEventPublisher;
    @Mock private AuditService auditService;

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

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("create: deve lançar exceção quando loadId não existe")
    void create_shouldThrow_whenLoadNotFound() {
        UUID loadId = UUID.randomUUID();
        CreateAuctionRequest request = new CreateAuctionRequest(loadId, 30);
        when(loadRepository.findById(loadId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> auctionService.create(request, UUID.randomUUID()));

        verify(auctionRepository, never()).save(any());
        verify(auctionEventPublisher, never()).publishAuctionOpened(any());
        verify(auditService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("create: deve lançar exceção quando já existe leilão OPEN para o load")
    void create_shouldThrow_whenLoadAlreadyHasOpenAuction() {
        UUID loadId = UUID.randomUUID();
        Load load = buildLoad(loadId);
        CreateAuctionRequest request = new CreateAuctionRequest(loadId, 30);

        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(auctionRepository.existsByLoadIdAndStatus(loadId, AuctionStatus.OPEN)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> auctionService.create(request, UUID.randomUUID()));

        verify(auctionRepository, never()).save(any());
        verify(auctionEventPublisher, never()).publishAuctionOpened(any());
        verify(auditService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("create: deve salvar leilão, publicar evento e salvar auditoria")
    void create_shouldSaveAndPublishEvents_whenValid() {
        UUID loadId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Load load = buildLoad(loadId);
        CreateAuctionRequest request = new CreateAuctionRequest(loadId, 30);
        Auction saved = Auction.builder()
                .id(UUID.randomUUID())
                .status(AuctionStatus.OPEN)
                .load(load)
                .createdByUserId(userId)
                .build();
        AuctionResponse expectedResponse = toResponse(saved);

        when(loadRepository.findById(loadId)).thenReturn(Optional.of(load));
        when(auctionRepository.existsByLoadIdAndStatus(loadId, AuctionStatus.OPEN)).thenReturn(false);
        when(auctionMapper.toEntity(request, load, userId)).thenReturn(saved);
        when(auctionRepository.save(saved)).thenReturn(saved);
        when(auctionMapper.toResponse(saved)).thenReturn(expectedResponse);

        AuctionResponse response = auctionService.create(request, userId);

        assertEquals(AuctionStatus.OPEN, response.status());
        assertEquals(loadId, response.loadId());
        verify(auctionRepository).save(saved);
        verify(auctionEventPublisher).publishAuctionOpened(any());
        verify(auditService).save(eq("AUCTION_OPENED"), eq(saved.getId()), any());
    }

    // -------------------------------------------------------------------------
    // close
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("close: deve fechar leilão com vencedor quando existe melhor lance")
    void close_shouldCloseWithWinner_whenBestBidExists() {
        UUID auctionId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        Auction auction = buildAuction(auctionId, AuctionStatus.OPEN, null);
        BestBidResponse bestBid = new BestBidResponse(
                UUID.randomUUID(), auctionId, carrierId, new BigDecimal("777.77"), Instant.now()
        );
        when(auctionRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(auction));
        when(bidClient.findBestBid(auctionId)).thenReturn(Optional.of(bestBid));
        when(auctionRepository.save(auction)).thenReturn(auction);
        when(auctionMapper.toResponse(auction)).thenAnswer(inv -> toResponse(auction));

        AuctionResponse response = auctionService.close(auctionId);

        assertEquals(AuctionStatus.CLOSED, response.status());
        assertEquals(carrierId, response.winnerCarrierId());
        assertEquals(new BigDecimal("777.77"), response.winningAmount());
        verify(redisTemplate).convertAndSend(eq("auction.closed"), anyString());
        verify(auctionEventPublisher).publishAuctionClosed(any());
        verify(auditService).save(eq("AUCTION_CLOSED"), eq(auctionId), any());
    }

    @Test
    @DisplayName("close: deve fechar leilão sem vencedor quando não há lances")
    void close_shouldCloseWithoutWinner_whenNoBidExists() {
        UUID auctionId = UUID.randomUUID();
        Auction auction = buildAuction(auctionId, AuctionStatus.OPEN, null);

        when(auctionRepository.findById(auctionId)).thenReturn(Optional.of(auction));
        when(bidClient.findBestBid(auctionId)).thenReturn(Optional.empty());
        when(auctionRepository.save(auction)).thenReturn(auction);
        when(auctionMapper.toResponse(auction)).thenAnswer(inv -> toResponse(auction));

        AuctionResponse response = auctionService.close(auctionId);

        assertEquals(AuctionStatus.CLOSED, response.status());
        assertNull(response.winnerCarrierId());
        assertNull(response.winningAmount());
        verify(redisTemplate).convertAndSend(eq("auction.closed"), anyString());
        verify(auctionEventPublisher).publishAuctionClosed(any());
        verify(auditService).save(eq("AUCTION_CLOSED"), eq(auctionId), any());
    }

    @Test
    @DisplayName("close: deve lançar exceção quando leilão já está fechado")
    void close_shouldThrow_whenAuctionAlreadyClosed() {
        UUID auctionId = UUID.randomUUID();
        Auction auction = Auction.builder().id(auctionId).status(AuctionStatus.CLOSED).build();
        when(auctionRepository.findByIdForUpdate(auctionId)).thenReturn(Optional.of(auction));

        assertThrows(IllegalStateException.class, () -> auctionService.close(auctionId));

        verify(bidClient, never()).findBestBid(any());
        verify(auctionRepository, never()).save(any());
        verify(redisTemplate, never()).convertAndSend(any(), any());
        verify(auctionEventPublisher, never()).publishAuctionClosed(any());
        verify(auditService, never()).save(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findById: deve lançar exceção quando leilão não existe")
    void findById_shouldThrow_whenAuctionNotFound() {
        UUID auctionId = UUID.randomUUID();
        when(auctionRepository.findById(auctionId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> auctionService.findById(auctionId));
    }

    @Test
    @DisplayName("findAll: deve retornar lista mapeada de leilões")
    void findAll_shouldReturnMappedList() {
        Auction a1 = buildAuction(UUID.randomUUID(), AuctionStatus.OPEN, null);
        Auction a2 = buildAuction(UUID.randomUUID(), AuctionStatus.CLOSED, null);

        when(auctionRepository.findAll()).thenReturn(List.of(a1, a2));
        when(auctionMapper.toResponse(a1)).thenReturn(toResponse(a1));
        when(auctionMapper.toResponse(a2)).thenReturn(toResponse(a2));

        List<AuctionResponse> result = auctionService.findAll();

        assertEquals(2, result.size());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Load buildLoad(UUID loadId) {
        Load load = new Load();
        load.setId(loadId);
        load.setInitialPrice(new BigDecimal("1000.00"));
        return load;
    }

    private Auction buildAuction(UUID id, AuctionStatus status, Load load) {
        return Auction.builder()
                .id(id)
                .status(status)
                .load(load)
                .build();
    }

    private AuctionResponse toResponse(Auction auction) {
        return new AuctionResponse(
                auction.getId(),
                auction.getLoad() != null ? auction.getLoad().getId() : null,
                auction.getStatus(),
                auction.getLoad() != null ? auction.getLoad().getInitialPrice() : null,
                auction.getStartedAt(),
                auction.getDurationMinutes(),
                auction.getClosedAt(),
                auction.getCreatedByUserId(),
                auction.getWinnerCarrierId(),
                auction.getWinningAmount()
        );
    }
}