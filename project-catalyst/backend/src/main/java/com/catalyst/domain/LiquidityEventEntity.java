package com.catalyst.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "liquidity_events")
@Getter
@Setter
@NoArgsConstructor
public class LiquidityEventEntity {
    @Id
    private String id;
    private String idempotencyKey;
    private String correlationId;
    @Enumerated(EnumType.STRING)
    private LiquidityEventType eventType;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String source;
    private String memo;
    @Enumerated(EnumType.STRING)
    private EventStatus status;
    private Instant occurredAt;
    private Instant receivedAt;
    private String recommendationId;
}
