package com.catalyst.decision;

import com.catalyst.domain.LiquidityEventType;
import com.catalyst.domain.RiskProfile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class TestData {
    private TestData() {
    }

    public static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    public static LiquidityEvent event(LiquidityEventType t, String amount) {
        return new LiquidityEvent("EVT-T1", t, "CUST-1001", bd(amount), "ZAR", Instant.now(), t.defaultSource());
    }

    /**
     * Thabo: post-inflow current balance is passed in because the inflow is credited before the decision.
     */
    public static FinancialContext thabo(String currentBalance) {
        return new FinancialContext("CUST-1001", "Thabo Mokoena", bd("42000"), RiskProfile.MODERATE, bd(currentBalance),
                bd("18000"), bd("50000"), bd("35000"), bd("32000"), bd("22"), 742, bd("95000"), bd("14.5"), bd("4500"),
                false, List.of());
    }

    public static ProductCatalog catalog() {
        List<Product> p = List.of(
                new Product("PRD-SAVINGS", "Savings Account", "SAVINGS", bd("0"), bd("5000000"), bd("6.75"), 0, "s"),
                new Product("PRD-FIXED", "Fixed Deposit", "INVESTMENT", bd("5000"), bd("5000000"), bd("8.1"), 0, "f"),
                new Product("PRD-MONEY-MKT", "Money Market", "INVESTMENT", bd("1000"), bd("5000000"), bd("7.4"), 1, "m"),
                new Product("PRD-INVEST", "Investment Account", "INVESTMENT", bd("1000"), bd("5000000"), bd("0"), 2, "i"),
                new Product("PRD-DEBT", "Debt Repayment", "DEBT", bd("100"), bd("5000000"), bd("0"), 0, "d"));
        return () -> p;
    }

    public static DecisionEngine engine() {
        DecisionProperties props = new DecisionProperties(null, null, null, null, null, null);
        return new DecisionEngine(List.of(new DebtRepaymentRule(props), new EmergencySavingsRule(),
                new InvestmentSuitabilityRule(props)), props);
    }
}