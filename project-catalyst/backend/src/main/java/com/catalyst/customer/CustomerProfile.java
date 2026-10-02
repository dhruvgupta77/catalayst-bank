package com.catalyst.customer;

import com.catalyst.domain.RiskProfile;

import java.math.BigDecimal;

public record CustomerProfile(String customerId, String name, int age, BigDecimal monthlySalary,
                              RiskProfile riskProfile, BigDecimal currentBalance, BigDecimal emergencySavings,
                              BigDecimal emergencyTarget, BigDecimal investmentBalance, BigDecimal loanBalance,
                              BigDecimal loanRate, BigDecimal monthlyLoanPayment) {
}
