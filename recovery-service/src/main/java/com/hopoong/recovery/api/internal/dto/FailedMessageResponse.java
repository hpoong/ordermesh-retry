package com.hopoong.recovery.api.internal.dto;

import com.hopoong.recovery.entity.FailedMessage;
import java.time.LocalDateTime;

public record FailedMessageResponse(
        Long id,
        String eventId,
        String consumerName,
        String queueName,
        String exchangeName,
        String routingKey,
        String payload,
        String failureType,
        String failureReason,
        Integer retryCount,
        Integer maxRetryCount,
        LocalDateTime lastFailedAt,
        String dlqStoredYn,
        String reprocessStatus,
        LocalDateTime reprocessedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FailedMessageResponse from(FailedMessage failedMessage) {
        return new FailedMessageResponse(
                failedMessage.getId(),
                failedMessage.getEventId(),
                failedMessage.getConsumerName(),
                failedMessage.getQueueName(),
                failedMessage.getExchangeName(),
                failedMessage.getRoutingKey(),
                failedMessage.getPayload(),
                failedMessage.getFailureType().name(),
                failedMessage.getFailureReason(),
                failedMessage.getRetryCount(),
                failedMessage.getMaxRetryCount(),
                failedMessage.getLastFailedAt(),
                failedMessage.getDlqStoredYn(),
                failedMessage.getReprocessStatus().name(),
                failedMessage.getReprocessedAt(),
                failedMessage.getCreatedAt(),
                failedMessage.getUpdatedAt()
        );
    }
}
