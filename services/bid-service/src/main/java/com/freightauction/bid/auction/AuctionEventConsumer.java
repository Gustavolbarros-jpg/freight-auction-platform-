package com.freightauction.bid.auction;

import com.freightauction.bid.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AuctionEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuctionEventConsumer.class);

    private final AuctionCacheService auctionCacheService;

    public AuctionEventConsumer(AuctionCacheService auctionCacheService) {
        this.auctionCacheService = auctionCacheService;
    }

    @RabbitListener(queues = RabbitMqConfig.AUCTION_OPENED_QUEUE)
    public void onAuctionOpened(AuctionOpenedEvent event) {
        log.info("Received auction.opened event for auctionId={}", event.auctionId());
        auctionCacheService.saveAsOpen(event.auctionId(), event.initialPrice());
    }

    @RabbitListener(queues = RabbitMqConfig.AUCTION_CLOSED_QUEUE)
    public void onAuctionClosed(AuctionClosedEvent event) {
        log.info("Received auction.closed event for auctionId={}", event.auctionId());
        auctionCacheService.saveAsClosed(event.auctionId());
    }
}