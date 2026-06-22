package com.freightauction.bid.service;

import com.freightauction.bid.event.BidPlacedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class BestBidServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private BestBidService bestBidService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        bestBidService = new BestBidService(redisTemplate);
    }

    @Test
    void firstBidBecomesBestAndNotifies() {
        BidPlacedEvent event = event("850.00");
        when(valueOperations.get(anyString())).thenReturn(null);

        assertTrue(bestBidService.process(event));

        verify(valueOperations).set(anyString(), anyString());
        verify(redisTemplate).convertAndSend(eq("bid.validated"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void higherBidDoesNotReplaceCurrentBest() {
        BidPlacedEvent event = event("900.00");
        when(valueOperations.get(anyString())).thenReturn(storedBid("850.00"));

        assertFalse(bestBidService.process(event));

        verify(valueOperations, never()).set(anyString(), anyString());
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void equalBidKeepsTheFirstBid() {
        BidPlacedEvent event = event("850.00");
        when(valueOperations.get(anyString())).thenReturn(storedBid("850.00"));

        assertFalse(bestBidService.process(event));

        verify(valueOperations, never()).set(anyString(), anyString());
    }

    private BidPlacedEvent event(String amount) {
        return new BidPlacedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal(amount),
                Instant.parse("2026-06-21T12:00:00Z")
        );
    }

    private String storedBid(String amount) {
        return amount + "|" + UUID.randomUUID() + "|" + UUID.randomUUID() + "|2026-06-21T11:00:00Z";
    }
}
