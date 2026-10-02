package com.catalyst.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "financial_context")
@Getter
@Setter
@NoArgsConstructor
public class FinancialContextEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String eventId;
    private String customerId;
    private String contextJson;
    private boolean reducedContext;
    private Instant createdAt;
}
