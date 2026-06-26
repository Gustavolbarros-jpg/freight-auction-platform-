package com.freightauction.bid.service;

import com.freightauction.bid.audit.AuditService;
import com.freightauction.bid.auction.AuctionCacheService;
import com.freightauction.bid.domain.Bid;
import com.freightauction.bid.domain.BidStatus;
import com.freightauction.bid.dto.BidAcceptedResponse;
import com.freightauction.bid.dto.CreateBidRequest;
import com.freightauction.bid.event.BidPlacedEvent;
import com.freightauction.bid.exception.AuctionValidationException;
import com.freightauction.bid.messaging.BidEventPublisher;
import com.freightauction.bid.repository.BidRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class BidServiceTest {

    @Mock
    private BidEventPublisher publisher;
    @Mock
    private AuctionCacheService auctionCacheService;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private AuditService auditService;

    private BidService bidService;

    @BeforeEach
    void setUp() {
        bidService = new BidService(publisher, auctionCacheService, bidRepository, auditService);
    }

    @Test
    void openAuctionPersistsPublishesAndAuditsBid() {
        UUID auctionId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        CreateBidRequest request = new CreateBidRequest(auctionId, new BigDecimal("850.00"));
        when(auctionCacheService.isUnknown(auctionId)).thenReturn(false);
        when(auctionCacheService.isOpen(auctionId)).thenReturn(true);
        when(auctionCacheService.getInitialPrice(auctionId)).thenReturn(Optional.of(new BigDecimal("1000.00")));
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BidAcceptedResponse response = bidService.placeBid(request, carrierId);

        assertEquals("QUEUED", response.status());
        ArgumentCaptor<Bid> bidCaptor = ArgumentCaptor.forClass(Bid.class);
        verify(bidRepository).save(bidCaptor.capture());
        assertEquals(BidStatus.RECEIVED, bidCaptor.getValue().getStatus());
        assertEquals(carrierId, bidCaptor.getValue().getCarrierId());

        ArgumentCaptor<BidPlacedEvent> eventCaptor = ArgumentCaptor.forClass(BidPlacedEvent.class);
        verify(publisher).publish(eventCaptor.capture());
        assertEquals(response.bidId(), eventCaptor.getValue().bidId());
        verify(auditService).save(eq("BID_RECEIVED"), eq(auctionId), any());
    }

    @Test
    void closedAuctionIsRejectedWithoutPersistencePublicationOrAudit() {
        UUID auctionId = UUID.randomUUID();
        CreateBidRequest request = new CreateBidRequest(auctionId, BigDecimal.TEN);
        when(auctionCacheService.isUnknown(auctionId)).thenReturn(false);
        when(auctionCacheService.isOpen(auctionId)).thenReturn(false);

        AuctionValidationException exception = assertThrows(
                AuctionValidationException.class,
                () -> bidService.placeBid(request, UUID.randomUUID())
        );

        assertEquals(AuctionValidationException.Reason.AUCTION_CLOSED, exception.getReason());
        verify(bidRepository, never()).save(any());
        verify(publisher, never()).publish(any());
        verify(auditService, never()).save(any(), any(), any());
    }

    @Test
    void unknownAuctionIsRejectedWithoutPublication() {
        UUID auctionId = UUID.randomUUID();
        CreateBidRequest request = new CreateBidRequest(auctionId, BigDecimal.TEN);
        when(auctionCacheService.isUnknown(auctionId)).thenReturn(true);

        AuctionValidationException exception = assertThrows(
                AuctionValidationException.class,
                () -> bidService.placeBid(request, UUID.randomUUID())
        );

        assertEquals(AuctionValidationException.Reason.AUCTION_NOT_SYNCED, exception.getReason());
        verify(bidRepository, never()).save(any());
        verify(publisher, never()).publish(any());
        verify(auditService, never()).save(any(), any(), any());
    }
}
