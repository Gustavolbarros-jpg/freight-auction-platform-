package com.freightauction.auction.messaging;

import com.freightauction.auction.config.AuctionRabbitMqConfig;
import com.freightauction.auction.event.AuctionClosedEvent;
import com.freightauction.auction.event.AuctionOpenedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuctionEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public AuctionEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishAuctionOpened(AuctionOpenedEvent event) {
        rabbitTemplate.convertAndSend(
                AuctionRabbitMqConfig.AUCTION_EXCHANGE,
                AuctionRabbitMqConfig.AUCTION_OPENED_ROUTING_KEY,
                event
        );
    }

    public void publishAuctionClosed(AuctionClosedEvent event) {
        rabbitTemplate.convertAndSend(
                AuctionRabbitMqConfig.AUCTION_EXCHANGE,
                AuctionRabbitMqConfig.AUCTION_CLOSED_ROUTING_KEY,
                event
        );
    }
}