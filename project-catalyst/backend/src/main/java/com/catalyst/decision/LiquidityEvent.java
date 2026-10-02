package com.catalyst.decision;

import com.catalyst.domain.LiquidityEventType;

import java.math.BigDecimal;
import java.time.Instant;

public record LiquidityEvent(String eventId, LiquidityEventType eventType, String customerId,
                             BigDecimal amount, String currency, Instant timestamp, String source) {
}
