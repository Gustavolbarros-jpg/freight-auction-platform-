package com.freightauction.bid.messaging;

import com.freightauction.bid.config.RabbitMqConfig;
import com.freightauction.bid.event.BidPlacedEvent;
import com.freightauction.bid.service.BestBidService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BidPlacedConsumer {

    private final BestBidService bestBidService;

    public BidPlacedConsumer(BestBidService bestBidService) {
        this.bestBidService = bestBidService;
    }

    @RabbitListener(queues = RabbitMqConfig.BID_PLACED_QUEUE)
    public void consume(BidPlacedEvent event) {
        bestBidService.process(event);
    }
}
