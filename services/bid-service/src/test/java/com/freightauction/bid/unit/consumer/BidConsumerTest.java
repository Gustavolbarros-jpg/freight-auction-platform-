package com.freightauction.bid.unit.consumer;

import com.freightauction.bid.event.BidPlacedEvent;
import com.freightauction.bid.messaging.BidPlacedConsumer;
import com.freightauction.bid.service.BestBidService;
import com.freightauction.bid.service.BidService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidConsumerTest {

    @Mock private BestBidService bestBidService;
    @Mock private BidService bidService;

    private BidPlacedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BidPlacedConsumer(bestBidService, bidService);
    }

    @Test
    @DisplayName("consume: lance aceito como melhor deve marcar como VALIDATED")
    void consume_shouldMarkValidated_whenBidIsAcceptedAsBest() {
        BidPlacedEvent event = buildEvent();
        when(bestBidService.process(event)).thenReturn(true);

        consumer.consume(event);

        verify(bestBidService).process(event);
        verify(bidService).markProcessed(event.bidId(), true);
    }

    @Test
    @DisplayName("consume: lance rejeitado deve marcar como REJECTED")
    void consume_shouldMarkRejected_whenBidIsNotBest() {
        BidPlacedEvent event = buildEvent();
        when(bestBidService.process(event)).thenReturn(false);

        consumer.consume(event);

        verify(bestBidService).process(event);
        verify(bidService).markProcessed(event.bidId(), false);
    }

    @Test
    @DisplayName("consume: deve sempre chamar markProcessed independente do resultado")
    void consume_shouldAlwaysCallMarkProcessed() {
        BidPlacedEvent event = buildEvent();
        when(bestBidService.process(event)).thenReturn(true);

        consumer.consume(event);

        verify(bidService, times(1)).markProcessed(any(), anyBoolean());
    }

    private BidPlacedEvent buildEvent() {
        return new BidPlacedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("850.00"),
                Instant.now()
        );
    }
}