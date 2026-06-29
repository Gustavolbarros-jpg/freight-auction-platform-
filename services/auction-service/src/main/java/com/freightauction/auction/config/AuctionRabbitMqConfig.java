package com.freightauction.auction.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class AuctionRabbitMqConfig {

    public static final String AUCTION_EXCHANGE = "auction.exchange";
    public static final String AUCTION_OPENED_QUEUE = "auction.opened.queue";
    public static final String AUCTION_CLOSED_QUEUE = "auction.closed.queue";
    public static final String AUCTION_OPENED_ROUTING_KEY = "auction.opened";
    public static final String AUCTION_CLOSED_ROUTING_KEY = "auction.closed";

    @Bean
    public TopicExchange auctionExchange() {
        return new TopicExchange(AUCTION_EXCHANGE);
    }

    @Bean
    public Queue auctionOpenedQueue() {
        return new Queue(AUCTION_OPENED_QUEUE, true);
    }

    @Bean
    public Queue auctionClosedQueue() {
        return new Queue(AUCTION_CLOSED_QUEUE, true);
    }

    @Bean
    public Binding auctionOpenedBinding(Queue auctionOpenedQueue, TopicExchange auctionExchange) {
        return BindingBuilder
                .bind(auctionOpenedQueue)
                .to(auctionExchange)
                .with(AUCTION_OPENED_ROUTING_KEY);
    }

    @Bean
    public Binding auctionClosedBinding(Queue auctionClosedQueue, TopicExchange auctionExchange) {
        return BindingBuilder
                .bind(auctionClosedQueue)
                .to(auctionExchange)
                .with(AUCTION_CLOSED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
