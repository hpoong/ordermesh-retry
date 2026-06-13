package com.hopoong.core.config;

import com.hopoong.core.keys.rabbitmq.RabbitMqKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange userPointChangedExchange() {
        return new TopicExchange(RabbitMqKeys.UserPointChanged.EXCHANGE, true, false);
    }

    @Bean
    public Queue userPointChangedQueue() {
        return new Queue(RabbitMqKeys.UserPointChanged.QUEUE, true);
    }

    @Bean
    public Binding userPointChangedBinding(Queue userPointChangedQueue, TopicExchange userPointChangedExchange) {
        return BindingBuilder.bind(userPointChangedQueue)
                .to(userPointChangedExchange)
                .with(RabbitMqKeys.UserPointChanged.ROUTING_KEY);
    }

    @Bean
    public Queue userPointChangedProcessingQueue() {
        return new Queue(RabbitMqKeys.UserPointChanged.PROCESSING_QUEUE, true);
    }

    @Bean
    public Binding userPointChangedProcessingBinding(Queue userPointChangedProcessingQueue, TopicExchange userPointChangedExchange) {
        return BindingBuilder.bind(userPointChangedProcessingQueue)
                .to(userPointChangedExchange)
                .with(RabbitMqKeys.UserPointChanged.ROUTING_KEY);
    }

    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(rabbitMessageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter);

        return factory;
    }
}
