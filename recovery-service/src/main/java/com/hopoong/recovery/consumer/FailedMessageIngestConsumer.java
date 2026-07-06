package com.hopoong.recovery.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopoong.core.event.FailedMessageIngestEvent;
import com.hopoong.core.event.UserPointChangedEvent;
import com.hopoong.core.keys.rabbitmq.RabbitMqKeys;
import com.hopoong.recovery.enums.FailureType;
import com.hopoong.recovery.ingest.FailedMessageIngestCommand;
import com.hopoong.recovery.ingest.FailedMessageIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class FailedMessageIngestConsumer {

    private static final String USER_POINT_CHANGED_CONSUMER = "UserPointChangedConsumer";

    private final FailedMessageIngestService failedMessageIngestService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMqKeys.FailedMessageIngest.QUEUE)
    public void consumeIngest(FailedMessageIngestEvent event) {
        failedMessageIngestService.ingest(new FailedMessageIngestCommand(
                event.eventId(),
                event.consumerName(),
                event.queueName(),
                event.exchangeName(),
                event.routingKey(),
                event.payload(),
                FailureType.from(event.failureType()),
                event.failureReason(),
                event.retryCount(),
                "N"
        ));

        log.info(
                "failed message ingest 완료. eventId={} consumerName={} failureType={}",
                event.eventId(),
                event.consumerName(),
                event.failureType()
        );
    }

    @RabbitListener(queues = RabbitMqKeys.UserPointChangedV2.DLQ)
    public void consumeUserPointChangedDlq(UserPointChangedEvent event) {
        failedMessageIngestService.ingest(new FailedMessageIngestCommand(
                event.eventId(),
                USER_POINT_CHANGED_CONSUMER,
                RabbitMqKeys.UserPointChangedV2.QUEUE,
                RabbitMqKeys.UserPointChangedV2.EXCHANGE,
                RabbitMqKeys.UserPointChangedV2.ROUTING_KEY,
                toPayload(event),
                FailureType.SYSTEM,
                "UserPointChanged main queue DLQ backup message",
                0,
                "Y"
        ));

        log.warn("UserPointChanged DLQ 메시지를 failed_messages에 백업 적재했습니다. eventId={}", event.eventId());
    }

    private String toPayload(UserPointChangedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("UserPointChangedEvent payload 직렬화 실패. eventId=" + event.eventId(), exception);
        }
    }
}
