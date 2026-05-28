package com.hopoong.order.api.user;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserPointChangedOutboxRequest(
        Long userId,
        BigDecimal changeAmount,
        BigDecimal balanceAfter,
        LocalDateTime occurredAt
) {
}
