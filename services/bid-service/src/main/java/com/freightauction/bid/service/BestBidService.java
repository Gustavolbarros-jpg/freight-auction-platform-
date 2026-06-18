package com.freightauction.bid.service;

import com.freightauction.bid.dto.BestBidResponse;
import com.freightauction.bid.event.BidPlacedEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class BestBidService {

    private static final String FIELD_SEPARATOR = "|";

    private final StringRedisTemplate redisTemplate;

    public BestBidService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void process(BidPlacedEvent event) {
        String key = "auction:%s:best_bid".formatted(event.auctionId());
        String currentBestBid = redisTemplate.opsForValue().get(key);

        if (currentBestBid == null || event.amount().compareTo(extractAmount(currentBestBid)) < 0) {
            redisTemplate.opsForValue().set(key, serialize(event));
        }
    }

    public BestBidResponse findBestBid(UUID auctionId) {
        String key = "auction:%s:best_bid".formatted(auctionId);
        String storedBid = redisTemplate.opsForValue().get(key);

        if (storedBid == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No bid found for auction");
        }

        String[] fields = storedBid.split("\\|");

        return new BestBidResponse(
                UUID.fromString(fields[1]),
                auctionId,
                UUID.fromString(fields[2]),
                new BigDecimal(fields[0]),
                Instant.parse(fields[3])
        );
    }

    private BigDecimal extractAmount(String storedBid) {
        return new BigDecimal(storedBid.split("\\|")[0]);
    }

    private String serialize(BidPlacedEvent event) {
        return event.amount()
                + FIELD_SEPARATOR + event.bidId()
                + FIELD_SEPARATOR + event.carrierId()
                + FIELD_SEPARATOR + event.receivedAt();
    }
}
