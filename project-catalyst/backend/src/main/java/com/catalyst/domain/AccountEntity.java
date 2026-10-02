package com.catalyst.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class AccountEntity {
    @Id
    private String id;
    private String customerId;
    @Enumerated(EnumType.STRING)
    private AccountType type;
    private String name;
    private BigDecimal balance;
    private BigDecimal interestRate;
}
