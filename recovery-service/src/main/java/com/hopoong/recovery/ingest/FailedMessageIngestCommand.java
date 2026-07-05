package com.hopoong.recovery.ingest;

import com.hopoong.recovery.enums.FailureType;

public record FailedMessageIngestCommand(
        String eventId,
        String consumerName,
        String queueName,
        String exchangeName,
        String routingKey,
        String payload,
        FailureType failureType,
        String failureReason,
        Integer retryCount,
        String dlqStoredYn
) {
}
