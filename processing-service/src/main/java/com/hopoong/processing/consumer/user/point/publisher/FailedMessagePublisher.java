package com.hopoong.processing.consumer.user.point.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopoong.core.event.FailedMessageIngestEvent;
import com.hopoong.core.event.UserPointChangedEvent;
import com.hopoong.core.keys.rabbitmq.RabbitMqKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class FailedMessagePublisher {

    public static final String CONSUMER_NAME = "UserPointChangedConsumer";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void publish(UserPointChangedEvent event, String failureType, String failureReason, Integer retryCount) {
        FailedMessageIngestEvent ingestEvent = new FailedMessageIngestEvent(
                event.eventId(),
                CONSUMER_NAME,
                RabbitMqKeys.UserPointChangedV2.QUEUE,
                RabbitMqKeys.UserPointChangedV2.EXCHANGE,
                RabbitMqKeys.UserPointChangedV2.ROUTING_KEY,
                toPayload(event),
                failureType,
                failureReason,
                retryCount == null ? 0 : retryCount
        );

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqKeys.FailedMessageIngest.EXCHANGE,
                    RabbitMqKeys.FailedMessageIngest.ROUTING_KEY,
                    ingestEvent
            );
        } catch (AmqpException exception) {
            log.error(
                    "failed message ingest publish 실패. eventId={} failureType={}",
                    event.eventId(),
                    failureType,
                    exception
            );
            throw exception;
        }
    }

    private String toPayload(UserPointChangedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("UserPointChangedEvent payload 직렬화 실패. eventId=" + event.eventId(), exception);
        }
    }
}
