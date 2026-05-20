package com.hopoong.core.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserPointChangedEvent(
        String eventId,
        String eventType,
        String eventVersion,
        Long userId,
        BigDecimal changeAmount,
        BigDecimal balanceAfter,
        LocalDateTime occurredAt
) {
}
