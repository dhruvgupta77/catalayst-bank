package com.catalyst.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "recommendations")
@Getter
@Setter
@NoArgsConstructor
public class RecommendationEntity {
    @Id
    private String id;
    private String eventId;
    private String customerId;
    @Enumerated(EnumType.STRING)
    private RecommendationStatus status;
    private String source;
    private String narrativeJson;
    private String rulesJson;
    private boolean reducedContext;
    private long generationMs;
    private Instant createdAt;
    private Instant viewedAt;
    private Instant decidedAt;
}
