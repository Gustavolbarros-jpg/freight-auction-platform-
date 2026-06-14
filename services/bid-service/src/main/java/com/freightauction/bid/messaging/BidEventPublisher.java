package com.freightauction.bid.messaging;

import com.freightauction.bid.config.RabbitMqConfig;
import com.freightauction.bid.event.BidPlacedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class BidEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public BidEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(BidPlacedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.BID_EXCHANGE,
                RabbitMqConfig.BID_PLACED_ROUTING_KEY,
                event
        );
    }
}
