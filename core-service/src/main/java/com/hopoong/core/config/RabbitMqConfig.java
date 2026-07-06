package com.hopoong.core.config;

import com.hopoong.core.keys.rabbitmq.RabbitMqKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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
        return new TopicExchange(RabbitMqKeys.UserPointChangedV2.EXCHANGE, true, false);
    }

    @Bean
    public Queue userPointChangedQueue() {
        return QueueBuilder.durable(RabbitMqKeys.UserPointChangedV2.QUEUE)
                .deadLetterExchange(RabbitMqKeys.UserPointChangedV2.EXCHANGE)
                .deadLetterRoutingKey(RabbitMqKeys.UserPointChangedV2.DLQ)
                .build();
    }

    @Bean
    public Binding userPointChangedBinding(Queue userPointChangedQueue, TopicExchange userPointChangedExchange) {
        return BindingBuilder.bind(userPointChangedQueue)
                .to(userPointChangedExchange)
                .with(RabbitMqKeys.UserPointChangedV2.ROUTING_KEY);
    }

    @Bean
    public Queue failedMessageIngestQueue() {
        return QueueBuilder.durable(RabbitMqKeys.FailedMessageIngest.QUEUE).build();
    }

    @Bean
    public Binding failedMessageIngestBinding(Queue failedMessageIngestQueue, TopicExchange userPointChangedExchange) {
        return BindingBuilder.bind(failedMessageIngestQueue)
                .to(userPointChangedExchange)
                .with(RabbitMqKeys.FailedMessageIngest.ROUTING_KEY);
    }

    @Bean
    public Queue userPointChangedDlq() {
        return QueueBuilder.durable(RabbitMqKeys.UserPointChangedV2.DLQ).build();
    }

    @Bean
    public Binding userPointChangedDlqBinding(Queue userPointChangedDlq, TopicExchange userPointChangedExchange) {
        return BindingBuilder.bind(userPointChangedDlq)
                .to(userPointChangedExchange)
                .with(RabbitMqKeys.UserPointChangedV2.DLQ);
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
