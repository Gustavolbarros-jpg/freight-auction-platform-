package com.freightauction.bid.service;

import com.freightauction.bid.client.AuctionClient;
import com.freightauction.bid.domain.Bid;
import com.freightauction.bid.domain.BidStatus;
import com.freightauction.bid.dto.AuctionSummaryResponse;
import com.freightauction.bid.dto.BidAcceptedResponse;
import com.freightauction.bid.dto.CreateBidRequest;
import com.freightauction.bid.event.BidPlacedEvent;
import com.freightauction.bid.messaging.BidEventPublisher;
import com.freightauction.bid.repository.BidRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock
    private BidEventPublisher publisher;
    @Mock
    private AuctionClient auctionClient;
    @Mock
    private BidRepository bidRepository;

    private BidService bidService;

    @BeforeEach
    void setUp() {
        bidService = new BidService(publisher, auctionClient, bidRepository);
    }

    @Test
    void openAuctionPersistsAndPublishesBid() {
        UUID auctionId = UUID.randomUUID();
        UUID carrierId = UUID.randomUUID();
        CreateBidRequest request = new CreateBidRequest(auctionId, new BigDecimal("850.00"));
        when(auctionClient.findById(auctionId)).thenReturn(new AuctionSummaryResponse(auctionId, "OPEN"));
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
    }

    @Test
    void closedAuctionIsRejectedWithoutPersistenceOrPublication() {
        UUID auctionId = UUID.randomUUID();
        CreateBidRequest request = new CreateBidRequest(auctionId, BigDecimal.TEN);
        when(auctionClient.findById(auctionId)).thenReturn(new AuctionSummaryResponse(auctionId, "CLOSED"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bidService.placeBid(request, UUID.randomUUID())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(bidRepository, never()).save(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void missingAuctionIsRejectedWithoutPublication() {
        UUID auctionId = UUID.randomUUID();
        CreateBidRequest request = new CreateBidRequest(auctionId, BigDecimal.TEN);
        when(auctionClient.findById(auctionId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Auction not found"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> bidService.placeBid(request, UUID.randomUUID())
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(publisher, never()).publish(any());
    }
}
