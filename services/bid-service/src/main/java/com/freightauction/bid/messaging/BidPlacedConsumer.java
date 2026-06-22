package com.freightauction.bid.messaging;

import com.freightauction.bid.config.RabbitMqConfig;
import com.freightauction.bid.event.BidPlacedEvent;
import com.freightauction.bid.service.BestBidService;
import com.freightauction.bid.service.BidService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BidPlacedConsumer {

    private final BestBidService bestBidService;
    private final BidService bidService;

    public BidPlacedConsumer(BestBidService bestBidService, BidService bidService) {
        this.bestBidService = bestBidService;
        this.bidService = bidService;
    }

    @RabbitListener(queues = RabbitMqConfig.BID_PLACED_QUEUE)
    public void consume(BidPlacedEvent event) {
        boolean acceptedAsBest = bestBidService.process(event);
        bidService.markProcessed(event.bidId(), acceptedAsBest);
    }
}
