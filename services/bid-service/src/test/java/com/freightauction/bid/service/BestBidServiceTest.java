package com.freightauction.bid.service;

import com.freightauction.bid.event.BidPlacedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BestBidServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private BestBidService bestBidService;

    @BeforeEach
    void setUp() {
        bestBidService = new BestBidService(redisTemplate);
    }

    // Agora a comparação GET+compare+SET roda dentro do Redis (script Lua),
    // então o teste simula o RESULTADO desse script (1 = se tornou o melhor lance,
    // 0 = não), em vez de mockar GET/SET separadamente como antes.

    @Test
    void firstBidBecomesBestAndNotifies() {
        BidPlacedEvent event = event("850.00");
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any()))
                .thenReturn(1L);

        assertTrue(bestBidService.process(event));

        verify(redisTemplate).convertAndSend(eq("bid.validated"), anyString());
    }

    @Test
    void higherBidDoesNotReplaceCurrentBest() {
        BidPlacedEvent event = event("900.00");
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any()))
                .thenReturn(0L);

        assertFalse(bestBidService.process(event));

        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void equalBidKeepsTheFirstBid() {
        BidPlacedEvent event = event("850.00");
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any()))
                .thenReturn(0L);

        assertFalse(bestBidService.process(event));

        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
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
}