package com.hopoong.processing.consumer.user.point.client;

import java.time.LocalDateTime;

public record UserPointChangedApplyRequest(
        String eventId,
        Long userId,
        Long orderId,
        String pointType,
        Integer changeAmount,
        Integer balanceAfter,
        LocalDateTime occurredAt
) {
}
