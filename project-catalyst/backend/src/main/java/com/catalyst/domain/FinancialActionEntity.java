package com.catalyst.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "financial_actions")
@Getter
@Setter
@NoArgsConstructor
public class FinancialActionEntity {
    @Id
    private String id;
    private String recommendationId;
    private String eventId;
    private String customerId;
    @Enumerated(EnumType.STRING)
    private ActionType actionType;
    private BigDecimal amount;
    private String productId;
    private String productName;
    private int priority;
    private String reason;
    private String impact;
    @Enumerated(EnumType.STRING)
    private ActionStatus status;
    private int progress;
    private String progressNote;
    private String failureReason;
    private Instant createdAt;
    private Instant executedAt;
}
