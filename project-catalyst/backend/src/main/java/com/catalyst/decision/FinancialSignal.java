package com.catalyst.decision;

/**
 * A transparent, rule-derived observation about the customer's finances. Not a prediction.
 * basis states the exact rule so the audience can see where it came from.
 */
public record FinancialSignal(String id, String title, String severity, String detail, String basis) {
}