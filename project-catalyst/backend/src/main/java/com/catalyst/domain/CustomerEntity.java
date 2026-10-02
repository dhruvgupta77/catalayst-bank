package com.catalyst.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class CustomerEntity {
    @Id
    private String id;
    private String name;
    private int age;
    private BigDecimal monthlySalary;
    @Enumerated(EnumType.STRING)
    private RiskProfile riskProfile;
    private BigDecimal monthlyLoanPayment;
    private BigDecimal emergencyFundTarget;
    private int creditScore;
}
