package com.freightauction.bid.auction;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuctionCacheService {

    static final String STATUS_KEY_TEMPLATE = "auction:%s:status";
    static final String INITIAL_PRICE_KEY_TEMPLATE = "auction:%s:initial_price";

    private final StringRedisTemplate redisTemplate;

    public AuctionCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> getStatus(UUID auctionId) {
        String value = redisTemplate.opsForValue().get(statusKey(auctionId));
        return Optional.ofNullable(value);
    }

    public boolean isOpen(UUID auctionId) {
        return getStatus(auctionId)
                .map("OPEN"::equals)
                .orElse(false);
    }

    public boolean isUnknown(UUID auctionId) {
        return getStatus(auctionId).isEmpty();
    }

    public Optional<BigDecimal> getInitialPrice(UUID auctionId) {
        String value = redisTemplate.opsForValue().get(initialPriceKey(auctionId));
        return Optional.ofNullable(value).map(BigDecimal::new);
    }

    public void saveAsOpen(UUID auctionId, BigDecimal initialPrice) {
        redisTemplate.opsForValue().set(statusKey(auctionId), "OPEN");
        redisTemplate.opsForValue().set(initialPriceKey(auctionId), initialPrice.toPlainString());
    }

    public void saveAsClosed(UUID auctionId) {
        redisTemplate.opsForValue().set(statusKey(auctionId), "CLOSED");
    }


    private String statusKey(UUID auctionId) {
        return STATUS_KEY_TEMPLATE.formatted(auctionId);
    }

    private String initialPriceKey(UUID auctionId) {
        return INITIAL_PRICE_KEY_TEMPLATE.formatted(auctionId);
    }
}