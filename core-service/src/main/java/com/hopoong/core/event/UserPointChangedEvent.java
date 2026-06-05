package com.hopoong.core.event;

import java.time.LocalDateTime;

public record UserPointChangedEvent(
        String eventId,
        String eventType,
        String eventVersion,
        Long userId,
        Long orderId,
        String pointType,
        Integer changeAmount,
        Integer balanceAfter,
        LocalDateTime occurredAt
) {
}
