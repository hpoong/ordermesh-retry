package com.hopoong.order.api.user;

import java.time.LocalDateTime;

public record UserPointChangedOutboxRequest(
        Long userId,
        Long orderId,
        String pointType,
        Integer changeAmount,
        Integer balanceAfter,
        LocalDateTime occurredAt
) {
}
