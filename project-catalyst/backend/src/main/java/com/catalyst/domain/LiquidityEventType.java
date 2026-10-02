package com.catalyst.domain;

import java.math.BigDecimal;

/**
 * Kinds of meaningful inbound transactions ("liquidity events"). Two-Pot is only one of them.
 */
public enum LiquidityEventType {
    SALARY("Salary", "42000", "EMPLOYER"),
    BONUS("Bonus", "15000", "EMPLOYER"),
    TWO_POT_PAYOUT("Two-Pot Withdrawal", "45000", "RETIREMENT_FUND"),
    TAX_REFUND("Tax Refund", "18500", "TAX_AUTHORITY"),
    INSURANCE_PAYOUT("Insurance Payout", "75000", "INSURER"),
    LARGE_TRANSFER("Large Inward Transfer", "30000", "BANK_TRANSFER");

    private final String label;
    private final BigDecimal defaultAmount;
    private final String defaultSource;

    LiquidityEventType(String label, String defaultAmount, String defaultSource) {
        this.label = label;
        this.defaultAmount = new BigDecimal(defaultAmount);
        this.defaultSource = defaultSource;
    }

    /** Accepts TWO_POT_WITHDRAWAL as an alias so the platform vocabulary and the stored enum name both work. */
    @com.fasterxml.jackson.annotation.JsonCreator
    public static LiquidityEventType fromJson(String value) {
        if (value == null) return null;
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        return "TWO_POT_WITHDRAWAL".equals(v) ? TWO_POT_PAYOUT : valueOf(v);
    }

    public String label() {
        return label;
    }

    public BigDecimal defaultAmount() {
        return defaultAmount;
    }

    public String defaultSource() {
        return defaultSource;
    }
}
