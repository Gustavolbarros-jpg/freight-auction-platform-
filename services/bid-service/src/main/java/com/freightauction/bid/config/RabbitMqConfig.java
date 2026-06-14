package com.freightauction.bid.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String BID_EXCHANGE = "bid.exchange";
    public static final String BID_PLACED_QUEUE = "bid.placed.queue";
    public static final String BID_PLACED_ROUTING_KEY = "bid.placed";

    @Bean
    public TopicExchange bidExchange() {
        return new TopicExchange(BID_EXCHANGE);
    }

    @Bean
    public Queue bidPlacedQueue() {
        return new Queue(BID_PLACED_QUEUE, true);
    }

    @Bean
    public Binding bidPlacedBinding(Queue bidPlacedQueue, TopicExchange bidExchange) {
        return BindingBuilder
                .bind(bidPlacedQueue)
                .to(bidExchange)
                .with(BID_PLACED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            SimpleRabbitListenerContainerFactoryConfigurer configurer
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        return factory;
    }
}
