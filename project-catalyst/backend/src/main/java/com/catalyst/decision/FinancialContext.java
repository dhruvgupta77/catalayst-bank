package com.catalyst.decision;

import com.catalyst.domain.RiskProfile;

import java.math.BigDecimal;
import java.util.List;

/**
 * Enriched customer picture used by the rules. creditCard* may be null when no bureau data is available.
 */
public record FinancialContext(String customerId, String customerName, BigDecimal monthlySalary,
                               RiskProfile riskProfile, BigDecimal currentBalance, BigDecimal emergencySavings,
                               BigDecimal emergencyTarget, BigDecimal investmentBalance,
                               BigDecimal creditCardBalance, BigDecimal creditCardRate, Integer creditScore,
                               BigDecimal loanBalance, BigDecimal loanRate, BigDecimal monthlyLoanPayment,
                               boolean reducedContext, List<String> notes) {
}
