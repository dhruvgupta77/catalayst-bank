package com.catalyst.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "consents")
@Getter
@Setter
@NoArgsConstructor
public class ConsentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String recommendationId;
    private String actionId;
    private String customerId;
    private String statement;
    private String actor;
    private String channel;
    private Instant grantedAt;
    private Instant expiresAt;
}
