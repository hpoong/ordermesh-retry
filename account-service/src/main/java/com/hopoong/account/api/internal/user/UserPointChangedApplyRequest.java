package com.hopoong.account.api.internal.user;

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
