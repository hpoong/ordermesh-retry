package com.hopoong.recovery.reprocess;

import com.hopoong.recovery.entity.FailedMessage;
import java.time.LocalDateTime;

public record FailedMessageReprocessResult(
        Long id,
        String eventId,
        String exchangeName,
        String routingKey,
        String reprocessStatus,
        LocalDateTime reprocessedAt,
        String failureReason
) {

    public static FailedMessageReprocessResult from(FailedMessage failedMessage) {
        return new FailedMessageReprocessResult(
                failedMessage.getId(),
                failedMessage.getEventId(),
                failedMessage.getExchangeName(),
                failedMessage.getRoutingKey(),
                failedMessage.getReprocessStatus().name(),
                failedMessage.getReprocessedAt(),
                failedMessage.getFailureReason()
        );
    }
}
