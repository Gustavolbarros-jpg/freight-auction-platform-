package com.freightauction.bid.service;

import com.freightauction.bid.dto.BestBidResponse;
import com.freightauction.bid.event.BidPlacedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class BestBidService {

    private static final String FIELD_SEPARATOR = "|";

    private final StringRedisTemplate redisTemplate;

    public BestBidService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean process(BidPlacedEvent event) {
        log.info("Processing bid: bidId={}, auctionId={}, carrierId={}, amount={}", event.bidId(), event.auctionId(), event.carrierId(), event.amount());
        String key = "auction:%s:best_bid".formatted(event.auctionId());
        String currentBestBid = redisTemplate.opsForValue().get(key);

        if (currentBestBid == null
                || event.amount().compareTo(extractAmount(currentBestBid)) < 0) {

            redisTemplate.opsForValue().set(key, serialize(event));

            redisTemplate.convertAndSend(
                    "bid.validated",
                    serializeNotification(event)
            );

            log.info("Best bid updated: bidId={}, auctionId={}, carrierId={}, amount={}", event.bidId(), event.auctionId(), event.carrierId(), event.amount());
            return true;
        } else {
            log.info("Bid processed without replacing best bid: bidId={}, auctionId={}, amount={}", event.bidId(), event.auctionId(), event.amount());
            return false;
        }
    }

    public BestBidResponse findBestBid(UUID auctionId) {
        log.info("Finding best bid: auctionId={}", auctionId);
        String key = "auction:%s:best_bid".formatted(auctionId);
        String storedBid = redisTemplate.opsForValue().get(key);

        if (storedBid == null) {
            log.warn("Best bid not found: auctionId={}", auctionId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No bid found for auction");
        }

        String[] fields = storedBid.split("\\|");

        BestBidResponse response = new BestBidResponse(
                UUID.fromString(fields[1]),
                auctionId,
                UUID.fromString(fields[2]),
                new BigDecimal(fields[0]),
                Instant.parse(fields[3])
        );
        log.info("Best bid found: bidId={}, auctionId={}, carrierId={}, amount={}", response.bidId(), response.auctionId(), response.carrierId(), response.amount());
        return response;
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

    private String serializeNotification(BidPlacedEvent event) {
        return """
                {
                  "auctionId": "%s",
                  "bidId": "%s",
                  "carrierId": "%s",
                  "amount": %s,
                  "receivedAt": "%s"
                }
                """.formatted(
                event.auctionId(),
                event.bidId(),
                event.carrierId(),
                event.amount(),
                event.receivedAt()
        );
    }
}
