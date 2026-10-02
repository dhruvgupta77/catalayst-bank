package com.catalyst.event;

import java.math.BigDecimal;
import java.time.Instant;

public record LiquidityEventMessage(String eventId, String correlationId, String eventType, String customerId,
                                    BigDecimal amount, String currency, Instant timestamp, String source) {
}
